package com.aiincident.logprocessor.incident;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.repository.LogEventRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncidentCorrelationServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentEvidenceRepository evidenceRepository;

    @Mock
    private LogEventRepository logEventRepository;

    private IncidentProperties properties;
    private ServiceDependencyGraph dependencyGraph;
    private EventTypeClassifier eventTypeClassifier;
    private IncidentCorrelationService correlationService;

    @BeforeEach
    void setUp() {
        properties = new IncidentProperties();
        properties.setCorrelationWindowSeconds(60);
        properties.setActiveWindowMinutes(15);
        properties.setMaxIncidentWindowMinutes(30);
        properties.setCrossServiceCorrelationEnabled(true);
        properties.setAutoCorrelateEvents(true);

        dependencyGraph = new ServiceDependencyGraph();
        eventTypeClassifier = new EventTypeClassifier();

        correlationService = new IncidentCorrelationService(
                incidentRepository,
                evidenceRepository,
                logEventRepository,
                properties,
                dependencyGraph,
                eventTypeClassifier
        );
    }

    @Test
    @DisplayName("Should group cascading failure events into a single incident with all evidence attached")
    void testCascadingFailureGroupingIntoSingleIncident() {
        // Cascading sequence from the assignment example:
        // 20:03:18 payment-service: DB timeout
        // 20:03:19 payment-service: pool exhausted
        // 20:03:21 payment-service: payment failure
        // 20:03:25 order-service: order timeout
        Instant t1 = Instant.parse("2026-08-23T20:03:18Z");
        Instant t2 = Instant.parse("2026-08-23T20:03:19Z");
        Instant t3 = Instant.parse("2026-08-23T20:03:21Z");
        Instant t4 = Instant.parse("2026-08-23T20:03:25Z");

        ProcessedLogEvent e1 = new ProcessedLogEvent("e-1", t1, "payment-service", "ERROR", "DB_TIMEOUT", "trace-1", "DB timeout", null, t1);
        ProcessedLogEvent e2 = new ProcessedLogEvent("e-2", t2, "payment-service", "ERROR", "POOL_EXHAUSTED", "trace-1", "pool exhausted", null, t2);
        ProcessedLogEvent e3 = new ProcessedLogEvent("e-3", t3, "payment-service", "ERROR", "PAYMENT_FAILED", "trace-1", "payment failure", null, t3);
        ProcessedLogEvent e4 = new ProcessedLogEvent("e-4", t4, "order-service", "ERROR", "ORDER_TIMEOUT", "trace-1", "order timeout", null, t4);

        List<Incident> savedIncidents = new ArrayList<>();
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident inc = inv.getArgument(0);
            if (inc.getId() == null) {
                try {
                    java.lang.reflect.Field idField = Incident.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(inc, 100L);
                } catch (Exception ignored) {}
                savedIncidents.add(inc);
            }
            return inc;
        });

        when(incidentRepository.findByStatusIn(any())).thenAnswer(inv -> savedIncidents);
        when(evidenceRepository.findByTraceId("trace-1")).thenAnswer(inv -> {
            List<IncidentEvidence> evList = new ArrayList<>();
            for (Incident inc : savedIncidents) {
                evList.add(new IncidentEvidence(inc.getId(), "e-1", t1, "payment-service", "DB_TIMEOUT", AnomalySeverity.CRITICAL, "m", "trace-1", null));
            }
            return evList;
        });

        List<Incident> result = correlationService.correlateEvents(List.of(e1, e2, e3, e4));

        // ACCEPTANCE CRITERIA: A cascading failure produces one meaningful incident
        assertThat(result).hasSize(1);
        Incident incident = result.getFirst();

        assertThat(incident.getPrimaryService()).isEqualTo("payment-service");
        assertThat(incident.getSeverity()).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getStartedAt()).isEqualTo(t1);
        assertThat(incident.getLastEventAt()).isEqualTo(t4);
        assertThat(incident.getAffectedServices()).contains("payment-service", "order-service");
    }

    @Test
    @DisplayName("Should respect configurable time window and create separate incidents for events outside window")
    void testConfigurableTimeWindow() {
        properties.setCorrelationWindowSeconds(60);

        Instant t1 = Instant.parse("2026-08-23T12:00:00Z");
        Instant t2 = Instant.parse("2026-08-23T12:00:30Z"); // +30s -> within 60s window
        Instant t3 = Instant.parse("2026-08-23T14:00:00Z"); // +2h -> outside window

        ProcessedLogEvent e1 = new ProcessedLogEvent("e1", t1, "payment-service", "ERROR", "DB_TIMEOUT", "tr-1", "DB timeout", null, t1);
        ProcessedLogEvent e2 = new ProcessedLogEvent("e2", t2, "payment-service", "ERROR", "POOL_EXHAUSTED", "tr-2", "pool exhausted", null, t2);
        ProcessedLogEvent e3 = new ProcessedLogEvent("e3", t3, "payment-service", "ERROR", "DB_TIMEOUT", "tr-3", "New DB timeout", null, t3);

        List<Incident> storedIncidents = new ArrayList<>();
        long[] idSeq = {1L};

        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident inc = inv.getArgument(0);
            if (inc.getId() == null) {
                try {
                    java.lang.reflect.Field idField = Incident.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(inc, idSeq[0]++);
                } catch (Exception ignored) {}
                storedIncidents.add(inc);
            }
            return inc;
        });
        when(incidentRepository.findByStatusIn(any())).thenAnswer(inv -> storedIncidents);

        List<Incident> result = correlationService.correlateEvents(List.of(e1, e2, e3));

        // e1 and e2 correlate into incident 1, e3 creates incident 2
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Should ignore informational non-failure events")
    void testIgnoreNonFailureEvents() {
        Instant t = Instant.now();
        ProcessedLogEvent infoEvent = new ProcessedLogEvent("info-1", t, "order-service", "INFO", "ORDER_CREATED", "tr-1", "Order created successfully", null, t);

        Optional<Incident> result = correlationService.correlateLogEvent(infoEvent);
        assertThat(result).isEmpty();
        verify(incidentRepository, never()).save(any());
        verify(evidenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should not correlate into RESOLVED or CLOSED incidents and should create a new incident instead")
    void testActiveIncidentStateConstraint() {
        Instant t1 = Instant.parse("2026-08-23T12:00:00Z");
        Instant t2 = Instant.parse("2026-08-23T12:00:10Z");

        Incident resolvedIncident = new Incident("Old Incident", AnomalySeverity.HIGH, IncidentStatus.RESOLVED, "payment-service", t1, t1, "desc", "DB_TIMEOUT");
        resolvedIncident.setResolvedAt(t1);

        when(incidentRepository.findByStatusIn(any())).thenReturn(List.of()); // No OPEN or INVESTIGATING incidents

        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        ProcessedLogEvent newEvent = new ProcessedLogEvent("e-new", t2, "payment-service", "ERROR", "DB_TIMEOUT", "tr-x", "New error", null, t2);

        Optional<Incident> result = correlationService.correlateLogEvent(newEvent);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(result.get().getStartedAt()).isEqualTo(t2);
    }

    @Test
    @DisplayName("Should dynamically upgrade incident severity when a higher-severity failure occurs")
    void testSeverityUpgrade() {
        Instant t1 = Instant.parse("2026-08-23T12:00:00Z");
        Instant t2 = Instant.parse("2026-08-23T12:00:15Z");

        ProcessedLogEvent warnEvent = new ProcessedLogEvent("e-warn", t1, "payment-service", "WARN", "PAYMENT_FAILED", "tr-1", "Payment failed warning", null, t1);
        ProcessedLogEvent critEvent = new ProcessedLogEvent("e-crit", t2, "payment-service", "ERROR", "DB_TIMEOUT", "tr-1", "Database timeout", null, t2);

        List<Incident> storedIncidents = new ArrayList<>();
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident inc = inv.getArgument(0);
            if (inc.getId() == null) {
                try {
                    java.lang.reflect.Field idField = Incident.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(inc, 1L);
                } catch (Exception ignored) {}
                storedIncidents.add(inc);
            }
            return inc;
        });
        when(incidentRepository.findByStatusIn(any())).thenAnswer(inv -> storedIncidents);

        List<Incident> incidents = correlationService.correlateEvents(List.of(warnEvent, critEvent));

        assertThat(incidents).hasSize(1);
        assertThat(incidents.getFirst().getSeverity()).isEqualTo(AnomalySeverity.CRITICAL);
    }
}
