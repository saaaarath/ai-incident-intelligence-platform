package com.aiincident.logprocessor.incident;

import com.aiincident.logprocessor.anomaly.AnomalyEvent;
import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    private IncidentProperties properties;
    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        properties = new IncidentProperties();
        properties.setMinIncidentSeverity(AnomalySeverity.MEDIUM);
        properties.setAutoCreateOnAnomaly(true);
        properties.setActiveWindowMinutes(15);
        incidentService = new IncidentService(incidentRepository, properties);
    }

    @Test
    @DisplayName("Should create new Incident in OPEN status when anomaly crosses threshold")
    void testIncidentCreation() {
        Instant startedAt = Instant.parse("2026-08-23T12:00:00Z");
        Instant detectedAt = Instant.parse("2026-08-23T12:01:00Z");

        AnomalyEvent anomaly = new AnomalyEvent(
                "errorRate",
                "order-service",
                0.80,
                0.0,
                0.0,
                0.05,
                detectedAt,
                AnomalySeverity.CRITICAL,
                startedAt,
                detectedAt,
                "Error rate anomaly detected for service 'order-service'"
        );

        when(incidentRepository.findFirstByPrimaryServiceAndStatusInOrderByStartedAtDesc(eq("order-service"), any()))
                .thenReturn(Optional.empty());
        when(incidentRepository.save(any(Incident.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Optional<Incident> result = incidentService.createOrCorrelateIncident(anomaly);

        assertThat(result).isPresent();
        Incident incident = result.get();
        assertThat(incident.getTitle()).isEqualTo("High Error Rate on order-service");
        assertThat(incident.getSeverity()).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getPrimaryService()).isEqualTo("order-service");
        assertThat(incident.getStartedAt()).isEqualTo(startedAt);
        assertThat(incident.getDetectedAt()).isEqualTo(detectedAt);
        assertThat(incident.getResolvedAt()).isNull();
        assertThat(incident.getMetric()).isEqualTo("errorRate");
    }

    @Test
    @DisplayName("Should assign proper severities and ignore anomalies below threshold")
    void testSeverityAssignmentAndFiltering() {
        Instant now = Instant.now();

        // LOW severity anomaly (below configured MEDIUM threshold)
        AnomalyEvent lowAnomaly = new AnomalyEvent(
                "errorRate", "payment-service", 0.02, 0.0, 0.0, 0.01,
                now, AnomalySeverity.LOW, now, now, "Low anomaly"
        );

        Optional<Incident> lowResult = incidentService.createOrCorrelateIncident(lowAnomaly);
        assertThat(lowResult).isEmpty();
        verify(incidentRepository, never()).save(any());

        // HIGH severity anomaly
        AnomalyEvent highAnomaly = new AnomalyEvent(
                "latencyAvg", "payment-service", 250.0, 50.0, 5.0, 100.0,
                now, AnomalySeverity.HIGH, now, now, "High latency anomaly"
        );

        when(incidentRepository.findFirstByPrimaryServiceAndStatusInOrderByStartedAtDesc(eq("payment-service"), any()))
                .thenReturn(Optional.empty());
        when(incidentRepository.save(any(Incident.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Optional<Incident> highResult = incidentService.createOrCorrelateIncident(highAnomaly);
        assertThat(highResult).isPresent();
        assertThat(highResult.get().getSeverity()).isEqualTo(AnomalySeverity.HIGH);
        assertThat(highResult.get().getTitle()).isEqualTo("Latency Spike on payment-service");
    }

    @Test
    @DisplayName("Should prevent duplicate incidents for same active failure window and upgrade severity")
    void testDuplicateIncidentPrevention() {
        Instant startedAt = Instant.parse("2026-08-23T12:00:00Z");
        Instant now = Instant.parse("2026-08-23T12:05:00Z");

        // Existing active incident in OPEN status with MEDIUM severity
        Incident existingIncident = new Incident(
                "High Error Rate on inventory-service",
                AnomalySeverity.MEDIUM,
                IncidentStatus.OPEN,
                "inventory-service",
                startedAt,
                startedAt,
                "Initial anomaly",
                "errorRate"
        );

        when(incidentRepository.findFirstByPrimaryServiceAndStatusInOrderByStartedAtDesc(eq("inventory-service"), any()))
                .thenReturn(Optional.of(existingIncident));
        when(incidentRepository.save(any(Incident.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Subsequent anomaly in same active window with higher severity (CRITICAL)
        AnomalyEvent secondAnomaly = new AnomalyEvent(
                "errorRate",
                "inventory-service",
                0.90,
                0.0,
                0.0,
                0.05,
                now,
                AnomalySeverity.CRITICAL,
                startedAt.plusSeconds(300),
                now,
                "Worsened error rate"
        );

        Optional<Incident> result = incidentService.createOrCorrelateIncident(secondAnomaly);

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(existingIncident);
        // Severity upgraded to CRITICAL
        assertThat(existingIncident.getSeverity()).isEqualTo(AnomalySeverity.CRITICAL);
    }

    @Test
    @DisplayName("Should manage complete lifecycle status transitions and set resolvedAt timestamp")
    void testLifecycleChanges() {
        Incident incident = new Incident(
                "Test Incident",
                AnomalySeverity.HIGH,
                IncidentStatus.OPEN,
                "order-service",
                Instant.now(),
                Instant.now(),
                "Desc",
                "errorRate"
        );

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Transition OPEN -> INVESTIGATING
        Incident investigating = incidentService.updateStatus(1L, IncidentStatus.INVESTIGATING);
        assertThat(investigating.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);
        assertThat(investigating.getResolvedAt()).isNull();

        // 2. Transition INVESTIGATING -> RESOLVED
        Incident resolved = incidentService.updateStatus(1L, IncidentStatus.RESOLVED);
        assertThat(resolved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();
        Instant resolvedTimestamp = resolved.getResolvedAt();

        // 3. Transition RESOLVED -> CLOSED
        Incident closed = incidentService.updateStatus(1L, IncidentStatus.CLOSED);
        assertThat(closed.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(closed.getResolvedAt()).isEqualTo(resolvedTimestamp);

        // 4. Reopen CLOSED -> OPEN
        Incident reopened = incidentService.updateStatus(1L, IncidentStatus.OPEN);
        assertThat(reopened.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(reopened.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("Should acknowledge OPEN incident to INVESTIGATING and fail if not in OPEN status")
    void testAcknowledgeIncident() {
        Incident incident = new Incident("Test", AnomalySeverity.HIGH, IncidentStatus.OPEN, "order-service", Instant.now(), Instant.now(), "d", "m");
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Incident acknowledged = incidentService.acknowledgeIncident(1L);
        assertThat(acknowledged.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);

        // Attempting to acknowledge again (already INVESTIGATING) should throw IllegalStateException
        assertThatThrownBy(() -> incidentService.acknowledgeIncident(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot acknowledge incident in status: INVESTIGATING");
    }

    @Test
    @DisplayName("Should resolve incident and set resolvedAt timestamp, and fail if already CLOSED")
    void testResolveIncident() {
        Incident incident = new Incident("Test", AnomalySeverity.HIGH, IncidentStatus.INVESTIGATING, "order-service", Instant.now(), Instant.now(), "d", "m");
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Incident resolved = incidentService.resolveIncident(1L);
        assertThat(resolved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();

        incident.setStatus(IncidentStatus.CLOSED);
        assertThatThrownBy(() -> incidentService.resolveIncident(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot resolve an already closed incident");
    }

    @Test
    @DisplayName("Should close incident and populate resolvedAt timestamp")
    void testCloseIncident() {
        Incident incident = new Incident("Test", AnomalySeverity.HIGH, IncidentStatus.OPEN, "order-service", Instant.now(), Instant.now(), "d", "m");
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Incident closed = incidentService.closeIncident(1L);
        assertThat(closed.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(closed.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should filter incidents across status, severity, service, and time range")
    void testFindIncidentsFiltering() {
        Instant t1 = Instant.parse("2026-08-23T12:00:00Z");
        Instant t2 = Instant.parse("2026-08-23T12:30:00Z");
        Instant t3 = Instant.parse("2026-08-23T13:00:00Z");

        Incident inc1 = new Incident("Inc 1", AnomalySeverity.CRITICAL, IncidentStatus.OPEN, "order-service", t1, t1, "d", "errorRate");
        Incident inc2 = new Incident("Inc 2", AnomalySeverity.HIGH, IncidentStatus.INVESTIGATING, "payment-service", t2, t2, "d", "latencyAvg");
        Incident inc3 = new Incident("Inc 3", AnomalySeverity.LOW, IncidentStatus.RESOLVED, "order-service", t3, t3, "d", "errorRate");

        when(incidentRepository.findAll()).thenReturn(List.of(inc1, inc2, inc3));

        // Filter by status
        List<Incident> openOnly = incidentService.findIncidents(IncidentStatus.OPEN, null, null, null, null);
        assertThat(openOnly).containsExactly(inc1);

        // Filter by severity
        List<Incident> highOnly = incidentService.findIncidents(null, AnomalySeverity.HIGH, null, null, null);
        assertThat(highOnly).containsExactly(inc2);

        // Filter by service
        List<Incident> orderOnly = incidentService.findIncidents(null, null, "order-service", null, null);
        assertThat(orderOnly).containsExactly(inc1, inc3);

        // Filter by time range
        List<Incident> timeFiltered = incidentService.findIncidents(null, null, null, t1.plusSeconds(60), t3.minusSeconds(60));
        assertThat(timeFiltered).containsExactly(inc2);
    }
}
