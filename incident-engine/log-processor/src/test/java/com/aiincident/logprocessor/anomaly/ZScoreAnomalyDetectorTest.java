package com.aiincident.logprocessor.anomaly;

import com.aiincident.logprocessor.metrics.LatencyMetrics;
import com.aiincident.logprocessor.metrics.OperationalMetrics;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ZScoreAnomalyDetectorTest {

    private ZScoreProperties properties;
    private ZScoreAnomalyDetector detector;

    @BeforeEach
    void setUp() {
        properties = new ZScoreProperties();
        properties.setThreshold(3.0);
        properties.setMinSamples(3);
        properties.setZeroSigmaMinDiff(0.05);
        properties.setEnabled(true);
        detector = new ZScoreAnomalyDetector(properties);
    }

    @Test
    @DisplayName("Formula Verification: Should calculate exact mean, standard deviation, and Z-Score on known dataset")
    void testExactZScoreCalculation() {
        // Dataset: [10, 20, 30, 40, 50] -> mean = 30.0, variance = 200.0, stdDev = sqrt(200) = 14.1421
        List<Double> baseline = List.of(10.0, 20.0, 30.0, 40.0, 50.0);

        // 1. Value at exactly mean (x = 30.0) -> z = 0.0
        ZScoreAnomalyDetector.ZScoreResult rMean = detector.calculateZScore(30.0, baseline);
        assertThat(rMean.mean()).isEqualTo(30.0);
        assertThat(rMean.stdDev()).isCloseTo(14.1421, within(0.001));
        assertThat(rMean.zScore()).isEqualTo(0.0);
        assertThat(rMean.isAnomalous()).isFalse();

        // 2. Value at 1 standard deviation (x = 30.0 + 14.1421 = 44.1421) -> z = 1.0
        ZScoreAnomalyDetector.ZScoreResult r1Sigma = detector.calculateZScore(44.1421, baseline);
        assertThat(r1Sigma.zScore()).isCloseTo(1.0, within(0.01));
        assertThat(r1Sigma.isAnomalous()).isFalse(); // 1.0 < 3.0

        // 3. Value at exactly 3 standard deviations (x = 30.0 + 3 * sqrt(200)) -> z = 3.0
        double x3Sigma = 30.0 + 3.0 * Math.sqrt(200.0);
        ZScoreAnomalyDetector.ZScoreResult r3Sigma = detector.calculateZScore(x3Sigma, baseline);
        assertThat(r3Sigma.zScore()).isCloseTo(3.0, within(0.01));
        assertThat(r3Sigma.isAnomalous()).isTrue(); // z >= 3.0 threshold
    }

    @Test
    @DisplayName("Zero Standard Deviation: Should handle constant baseline safely without divide-by-zero")
    void testZeroStandardDeviationSafety() {
        // Constant baseline of 0.0 (all windows had 0% error rate)
        List<Double> baseline = List.of(0.0, 0.0, 0.0, 0.0, 0.0);

        // Normal value: x = 0.0 -> z = 0.0, no anomaly
        ZScoreAnomalyDetector.ZScoreResult normal = detector.calculateZScore(0.0, baseline);
        assertThat(normal.mean()).isEqualTo(0.0);
        assertThat(normal.stdDev()).isEqualTo(0.0);
        assertThat(normal.zScore()).isEqualTo(0.0);
        assertThat(normal.isAnomalous()).isFalse();

        // Anomalous value: x = 0.50 (50% error rate spike)
        ZScoreAnomalyDetector.ZScoreResult spike = detector.calculateZScore(0.50, baseline);
        assertThat(spike.stdDev()).isEqualTo(0.0);
        assertThat(spike.isAnomalous()).isTrue();
        assertThat(spike.zScore()).isGreaterThanOrEqualTo(properties.getThreshold());

        // Minor noise below noise floor: x = 0.01 -> should not trigger anomaly
        ZScoreAnomalyDetector.ZScoreResult noise = detector.calculateZScore(0.01, baseline);
        assertThat(noise.isAnomalous()).isFalse();
    }

    @Test
    @DisplayName("Configurable Threshold: Changing threshold adjusts anomaly trigger sensitivity")
    void testConfigurableThreshold() {
        List<Double> baseline = List.of(10.0, 20.0, 30.0, 40.0, 50.0); // mean=30, stdDev=14.1421
        double x = 30.0 + (2.5 * 14.1421); // z = 2.5

        // Default threshold 3.0 -> z = 2.5 is NOT anomalous
        properties.setThreshold(3.0);
        ZScoreAnomalyDetector.ZScoreResult rDefault = detector.calculateZScore(x, baseline);
        assertThat(rDefault.zScore()).isCloseTo(2.5, within(0.01));
        assertThat(rDefault.isAnomalous()).isFalse();

        // Lowered threshold 2.0 -> z = 2.5 IS anomalous
        properties.setThreshold(2.0);
        ZScoreAnomalyDetector.ZScoreResult rLower = detector.calculateZScore(x, baseline);
        assertThat(rLower.isAnomalous()).isTrue();
    }

    @Test
    @DisplayName("Insufficient Samples: Should not detect anomalies if historical samples < minSamples")
    void testInsufficientSamples() {
        List<Double> sparseBaseline = List.of(10.0, 20.0); // 2 samples < minSamples=3
        ZScoreAnomalyDetector.ZScoreResult result = detector.calculateZScore(100.0, sparseBaseline);
        assertThat(result.sampleCount()).isEqualTo(0);
        assertThat(result.isAnomalous()).isFalse();
    }

    @Test
    @DisplayName("Operational Metrics: Detects error rate and latency anomalies via Z-score")
    void testOperationalMetricsZScoreDetection() {
        Instant now = Instant.now();

        // Baseline: 5 windows with ~2% error rate and ~40ms latency
        List<OperationalMetrics> baselineWindows = List.of(
                createMetrics("order-service", now.minusSeconds(300), 100, 2, 0.02, 40.0),
                createMetrics("order-service", now.minusSeconds(240), 100, 2, 0.02, 41.0),
                createMetrics("order-service", now.minusSeconds(180), 100, 1, 0.01, 39.0),
                createMetrics("order-service", now.minusSeconds(120), 100, 3, 0.03, 40.0),
                createMetrics("order-service", now.minusSeconds(60), 100, 2, 0.02, 40.0)
        );

        // Current: normal traffic
        OperationalMetrics normal = createMetrics("order-service", now, 100, 2, 0.02, 40.0);
        List<AnomalyEvent> normalAnomalies = detector.detectAnomalies("order-service", normal, baselineWindows, now);
        assertThat(normalAnomalies).isEmpty();

        // Current: error spike (30% errors)
        OperationalMetrics errorSpike = createMetrics("order-service", now, 100, 30, 0.30, 40.0);
        List<AnomalyEvent> spikeAnomalies = detector.detectAnomalies("order-service", errorSpike, baselineWindows, now);
        assertThat(spikeAnomalies).hasSize(1);
        assertThat(spikeAnomalies.getFirst().getMetric()).isEqualTo("errorRate");
        assertThat(spikeAnomalies.getFirst().getCurrentValue()).isEqualTo(0.30);
        assertThat(spikeAnomalies.getFirst().getSeverity()).isEqualTo(AnomalySeverity.CRITICAL);
    }

    private OperationalMetrics createMetrics(
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
