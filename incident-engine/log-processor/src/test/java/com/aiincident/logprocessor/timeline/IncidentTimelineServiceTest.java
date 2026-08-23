package com.aiincident.logprocessor.timeline;

import com.aiincident.logprocessor.anomaly.AnomalyEvent;
import com.aiincident.logprocessor.anomaly.AnomalyRepository;
import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.entity.ProcessedDeploymentEvent;
import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentEvidence;
import com.aiincident.logprocessor.incident.IncidentEvidenceRepository;
import com.aiincident.logprocessor.incident.IncidentRepository;
import com.aiincident.logprocessor.incident.IncidentStatus;
import com.aiincident.logprocessor.metrics.LatencyMetrics;
import com.aiincident.logprocessor.metrics.MetricsAggregationService;
import com.aiincident.logprocessor.metrics.OperationalMetrics;
import com.aiincident.logprocessor.repository.DeploymentEventRepository;
import com.aiincident.logprocessor.repository.LogEventRepository;
import java.time.Duration;
import java.time.Instant;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncidentTimelineServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentEvidenceRepository evidenceRepository;

    @Mock
    private AnomalyRepository anomalyRepository;

    @Mock
    private LogEventRepository logEventRepository;

    @Mock
    private DeploymentEventRepository deploymentRepository;

    @Mock
    private MetricsAggregationService metricsService;

    private IncidentTimelineService timelineService;

    @BeforeEach
    void setUp() {
        timelineService = new IncidentTimelineService(
                incidentRepository,
                evidenceRepository,
                anomalyRepository,
                logEventRepository,
                deploymentRepository,
                metricsService
        );
    }

    @Test
    @DisplayName("Should build unified chronological timeline combining anomalies, logs, deployments, failures, and metrics sorted by timestamp")
    void testBuildTimelineChronologicalOrdering() {
        Instant tDeploy = Instant.parse("2026-08-23T13:50:00Z");
        Instant tMetric = Instant.parse("2026-08-23T13:55:00Z");
        Instant tAnomaly = Instant.parse("2026-08-23T13:58:00Z");
        Instant tFailure = Instant.parse("2026-08-23T14:00:00Z");
        Instant tLog = Instant.parse("2026-08-23T14:02:00Z");

        Incident incident = new Incident(
                "Payment Service Outage",
                AnomalySeverity.CRITICAL,
                IncidentStatus.OPEN,
                "payment-service",
                tFailure,
                tFailure,
                "Database outage cascade",
                "DB_TIMEOUT"
        );
        incident.setAffectedServices(Set.of("payment-service", "order-service"));

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        // 1. Deployment
        ProcessedDeploymentEvent dep = new ProcessedDeploymentEvent(
                "dep-1", "DEPLOYMENT_COMPLETED", "payment-service", "v2.1.0", tDeploy, "tr-0", "{}", tDeploy
        );
        when(deploymentRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of(dep));

        // 2. Metrics
        LatencyMetrics latency = new LatencyMetrics(100L, 100.0, 5000.0, 2500.0, 2000.0, 4500.0, 4900.0);
        OperationalMetrics om = new OperationalMetrics(
                "payment-service", tMetric, tMetric.plusSeconds(60), 100L, 15L, 0.15, latency
        );
        when(metricsService.getMetrics(eq("payment-service"), any(), any(), any(Duration.class)))
                .thenReturn(List.of(om));

        // 3. Anomaly
        AnomalyEvent anomaly = new AnomalyEvent(
                "errorRate", "payment-service", 0.15, 0.01, 0.005, 0.05,
                tAnomaly, AnomalySeverity.HIGH, tAnomaly, tAnomaly.plusSeconds(60),
                "Error rate 15% breached 5% threshold"
        );
        when(anomalyRepository.findByDetectedAtBetween(any(), any()))
                .thenReturn(List.of(anomaly));

        // 4. Service Failure Evidence
        IncidentEvidence ev = new IncidentEvidence(
                1L, "ev-1", tFailure, "payment-service", "DB_TIMEOUT",
                AnomalySeverity.CRITICAL, "Hikari connection timeout after 3000ms", "tr-1", "{}"
        );
        incident.setEvidence(List.of(ev));
        when(evidenceRepository.findByIncidentIdOrderByTimestampAsc(any()))
                .thenReturn(List.of(ev));

        // 5. Logs (one duplicate of evidence that should be skipped, one unique log that should be included)
        ProcessedLogEvent logDup = new ProcessedLogEvent(
                "ev-1", tFailure, "payment-service", "ERROR", "DB_TIMEOUT", "tr-1", "Hikari connection timeout after 3000ms", "{}", tFailure
        );
        ProcessedLogEvent logOrder = new ProcessedLogEvent(
                "log-ord-1", tLog, "order-service", "WARN", "RETRY_EXHAUSTED", "tr-1", "Order retries exhausted for payment", "{}", tLog
        );
        when(logEventRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of(logDup, logOrder));

        // Build Timeline
        Optional<IncidentTimeline> timelineOpt = timelineService.buildTimeline(1L, 15, null);
        assertThat(timelineOpt).isPresent();

        IncidentTimeline timeline = timelineOpt.get();
        List<TimelineEvent> events = timeline.events();

        // Must contain 5 distinct categories
        assertThat(events).hasSize(5);

        // Verify strictly chronological order
        for (int i = 0; i < events.size() - 1; i++) {
            assertThat(events.get(i).timestamp()).isBeforeOrEqualTo(events.get(i + 1).timestamp());
        }

        // Verify order of event types
        assertThat(events.get(0).type()).isEqualTo(TimelineEventType.DEPLOYMENT);
        assertThat(events.get(0).timestamp()).isEqualTo(tDeploy);

        assertThat(events.get(1).type()).isEqualTo(TimelineEventType.METRIC);
        assertThat(events.get(1).timestamp()).isEqualTo(tMetric);

        assertThat(events.get(2).type()).isEqualTo(TimelineEventType.ANOMALY);
        assertThat(events.get(2).timestamp()).isEqualTo(tAnomaly);

        assertThat(events.get(3).type()).isEqualTo(TimelineEventType.SERVICE_FAILURE);
        assertThat(events.get(3).timestamp()).isEqualTo(tFailure);

        assertThat(events.get(4).type()).isEqualTo(TimelineEventType.LOG);
        assertThat(events.get(4).timestamp()).isEqualTo(tLog);

        // Verify deduplication (logDup with eventId "ev-1" was excluded from LOG items)
        assertThat(events.stream().filter(e -> e.type() == TimelineEventType.LOG)).hasSize(1);
    }
}
