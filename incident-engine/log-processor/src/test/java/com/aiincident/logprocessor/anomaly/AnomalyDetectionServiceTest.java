package com.aiincident.logprocessor.anomaly;

import com.aiincident.logprocessor.metrics.LatencyMetrics;
import com.aiincident.logprocessor.metrics.MetricsAggregationService;
import com.aiincident.logprocessor.metrics.OperationalMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock
    private MetricsAggregationService metricsService;

    @Mock
    private AnomalyRepository anomalyRepository;

    private AnomalyDetectionProperties properties;
    private AnomalyDetectionService detectionService;

    @BeforeEach
    void setUp() {
        properties = new AnomalyDetectionProperties();
        properties.setSigmaThreshold(3.0);
        properties.setErrorRateAbsoluteThreshold(0.05);
        properties.setLatencySpikeMultiplier(2.0);
        properties.setMinBaselineSamples(3);
        properties.setDefaultBaselineMinutes(15);

        detectionService = new AnomalyDetectionService(metricsService, anomalyRepository, properties);
    }

    @Test
    @DisplayName("Should accurately calculate baseline mean and standard deviation variability")
    void testBaselineCalculation() {
        List<Double> values = List.of(10.0, 12.0, 14.0, 16.0, 18.0);
        // mean = 14.0
        // squared diffs: (10-14)^2 = 16, (12-14)^2 = 4, (14-14)^2 = 0, (16-14)^2 = 4, (18-14)^2 = 16 -> sum = 40
        // variance = 40 / 5 = 8.0
        // std dev = sqrt(8) = 2.8284

        MetricBaseline baseline = detectionService.calculateBaseline("latencyAvg", "order-service", values);

        assertThat(baseline.metric()).isEqualTo("latencyAvg");
        assertThat(baseline.service()).isEqualTo("order-service");
        assertThat(baseline.mean()).isEqualTo(14.0);
        assertThat(baseline.variability()).isCloseTo(2.8284, within(0.001));
        assertThat(baseline.min()).isEqualTo(10.0);
        assertThat(baseline.max()).isEqualTo(18.0);
        assertThat(baseline.sampleCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("Normal Scenario: Should NOT generate anomalies during normal steady service behavior")
    void testNormalScenarioNoAnomalies() {
        Instant currentStart = Instant.parse("2026-08-23T12:00:00Z");
        Instant currentEnd = Instant.parse("2026-08-23T12:01:00Z");
        Instant baselineStart = Instant.parse("2026-08-23T11:45:00Z");
        Instant baselineEnd = Instant.parse("2026-08-23T12:00:00Z");

        // Baseline: 5 windows of normal traffic with 0 errors and latency around 50ms
        List<OperationalMetrics> baselineWindows = List.of(
                createWindowMetrics("order-service", baselineStart.plusSeconds(0), 10, 0, 0.0, 50.0),
                createWindowMetrics("order-service", baselineStart.plusSeconds(60), 12, 0, 0.0, 52.0),
                createWindowMetrics("order-service", baselineStart.plusSeconds(120), 11, 0, 0.0, 49.0),
                createWindowMetrics("order-service", baselineStart.plusSeconds(180), 10, 0, 0.0, 51.0),
                createWindowMetrics("order-service", baselineStart.plusSeconds(240), 13, 0, 0.0, 50.0)
        );

        // Current window: normal traffic with 0 errors and 51ms latency
        OperationalMetrics currentWindow = createWindowMetrics("order-service", currentStart, 12, 0, 0.0, 51.0);

        when(metricsService.getSummary(eq("order-service"), eq(currentStart), eq(currentEnd)))
                .thenReturn(currentWindow);
        when(metricsService.getMetrics(eq("order-service"), eq(baselineStart), eq(baselineEnd), any(Duration.class)))
                .thenReturn(baselineWindows);

        List<AnomalyEvent> anomalies = detectionService.detectAnomaliesForService(
                "order-service",
                currentStart,
                currentEnd,
                baselineStart,
                baselineEnd
        );

        assertThat(anomalies).isEmpty();
    }

    @Test
    @DisplayName("Abnormal Scenario (Error Spike): Should detect controlled error spike and generate anomaly event")
    void testAbnormalScenarioErrorSpike() {
        Instant currentStart = Instant.parse("2026-08-23T12:00:00Z");
        Instant currentEnd = Instant.parse("2026-08-23T12:01:00Z");
        Instant baselineStart = Instant.parse("2026-08-23T11:45:00Z");
        Instant baselineEnd = Instant.parse("2026-08-23T12:00:00Z");

        // Baseline: 5 windows of 0% error rate
        List<OperationalMetrics> baselineWindows = List.of(
                createWindowMetrics("order-service", baselineStart.plusSeconds(0), 10, 0, 0.0, 45.0),
                createWindowMetrics("order-service", baselineStart.plusSeconds(60), 10, 0, 0.0, 46.0),
                createWindowMetrics("order-service", baselineStart.plusSeconds(120), 10, 0, 0.0, 45.0),
                createWindowMetrics("order-service", baselineStart.plusSeconds(180), 10, 0, 0.0, 44.0),
                createWindowMetrics("order-service", baselineStart.plusSeconds(240), 10, 0, 0.0, 45.0)
        );

        // Current window: 50% error spike (5 errors out of 10 requests)
        OperationalMetrics currentWindow = createWindowMetrics("order-service", currentStart, 10, 5, 0.50, 45.0);

        when(metricsService.getSummary(eq("order-service"), eq(currentStart), eq(currentEnd)))
                .thenReturn(currentWindow);
        when(metricsService.getMetrics(eq("order-service"), eq(baselineStart), eq(baselineEnd), any(Duration.class)))
                .thenReturn(baselineWindows);

        List<AnomalyEvent> anomalies = detectionService.detectAnomaliesForService(
                "order-service",
                currentStart,
                currentEnd,
                baselineStart,
                baselineEnd
        );

        assertThat(anomalies).hasSize(1);
        AnomalyEvent event = anomalies.getFirst();

        assertThat(event.getMetric()).isEqualTo("errorRate");
        assertThat(event.getService()).isEqualTo("order-service");
        assertThat(event.getCurrentValue()).isEqualTo(0.50);
        assertThat(event.getBaselineMean()).isEqualTo(0.0);
        assertThat(event.getBaselineVariability()).isEqualTo(0.0);
        assertThat(event.getThreshold()).isEqualTo(0.05); // Absolute threshold applied
        assertThat(event.getSeverity()).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(event.getDetectedAt()).isNotNull();
        assertThat(event.getMessage()).contains("Error rate anomaly detected for service 'order-service'");
    }

    @Test
    @DisplayName("Abnormal Scenario (Latency Spike): Should detect latency spike and generate anomaly event")
    void testAbnormalScenarioLatencySpike() {
        Instant currentStart = Instant.parse("2026-08-23T12:00:00Z");
        Instant currentEnd = Instant.parse("2026-08-23T12:01:00Z");
        Instant baselineStart = Instant.parse("2026-08-23T11:45:00Z");
        Instant baselineEnd = Instant.parse("2026-08-23T12:00:00Z");

        // Baseline: 5 windows with average latency around 40ms
        List<OperationalMetrics> baselineWindows = List.of(
                createWindowMetrics("payment-service", baselineStart.plusSeconds(0), 10, 0, 0.0, 39.0),
                createWindowMetrics("payment-service", baselineStart.plusSeconds(60), 10, 0, 0.0, 41.0),
                createWindowMetrics("payment-service", baselineStart.plusSeconds(120), 10, 0, 0.0, 40.0),
                createWindowMetrics("payment-service", baselineStart.plusSeconds(180), 10, 0, 0.0, 40.0),
                createWindowMetrics("payment-service", baselineStart.plusSeconds(240), 10, 0, 0.0, 40.0)
        );

        // Current window: Latency jumps to 300ms (7.5x baseline)
        OperationalMetrics currentWindow = createWindowMetrics("payment-service", currentStart, 10, 0, 0.0, 300.0);

        when(metricsService.getSummary(eq("payment-service"), eq(currentStart), eq(currentEnd)))
                .thenReturn(currentWindow);
        when(metricsService.getMetrics(eq("payment-service"), eq(baselineStart), eq(baselineEnd), any(Duration.class)))
                .thenReturn(baselineWindows);

        List<AnomalyEvent> anomalies = detectionService.detectAnomaliesForService(
                "payment-service",
                currentStart,
                currentEnd,
                baselineStart,
                baselineEnd
        );

        assertThat(anomalies).hasSize(1);
        AnomalyEvent event = anomalies.getFirst();

        assertThat(event.getMetric()).isEqualTo("latencyAvg");
        assertThat(event.getService()).isEqualTo("payment-service");
        assertThat(event.getCurrentValue()).isEqualTo(300.0);
        assertThat(event.getBaselineMean()).isEqualTo(40.0);
        assertThat(event.getSeverity()).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(event.getDetectedAt()).isNotNull();
        assertThat(event.getMessage()).contains("Latency anomaly detected for service 'payment-service'");
    }

    private OperationalMetrics createWindowMetrics(
            String service,
            Instant start,
            long totalEvents,
            long errorCount,
            double errorRate,
            Double avgLatency) {
        LatencyMetrics latency = avgLatency != null
                ? new LatencyMetrics(totalEvents, avgLatency - 5, avgLatency + 5, avgLatency, avgLatency, avgLatency + 2, avgLatency + 4)
                : LatencyMetrics.empty();
        return new OperationalMetrics(
                service,
                start,
                start.plusSeconds(60),
                totalEvents,
                errorCount,
                errorRate,
                latency
        );
    }
}
