package com.aiincident.logprocessor.anomaly;

import com.aiincident.logprocessor.metrics.OperationalMetrics;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ZScoreAnomalyDetector {

    private static final Logger log = LoggerFactory.getLogger(ZScoreAnomalyDetector.class);

    private final ZScoreProperties properties;

    public ZScoreAnomalyDetector(ZScoreProperties properties) {
        this.properties = properties;
    }

    /**
     * Calculate Z-Score: z = (x - mean) / stdDev.
     * Safely handles zero standard deviation and insufficient samples.
     */
    public ZScoreResult calculateZScore(double x, List<Double> baselineValues) {
        if (baselineValues == null || baselineValues.size() < properties.getMinSamples()) {
            return new ZScoreResult(0.0, 0.0, 0.0, 0, false);
        }

        int n = baselineValues.size();
        double sum = 0.0;
        for (double v : baselineValues) {
            sum += v;
        }
        double mean = sum / n;

        double sumSquaredDiff = 0.0;
        for (double v : baselineValues) {
            sumSquaredDiff += Math.pow(v - mean, 2);
        }
        double stdDev = Math.sqrt(sumSquaredDiff / n);

        double z;
        boolean isAnomalous = false;

        if (stdDev > 0.000001) {
            // Standard Z-Score formula: z = (x - mean) / stdDev
            z = (x - mean) / stdDev;
            if (z >= properties.getThreshold()) {
                isAnomalous = true;
            }
        } else {
            // Zero standard deviation safe handling:
            double diff = x - mean;
            if (diff >= properties.getZeroSigmaMinDiff()) {
                // Meaningful increase above constant baseline
                z = (diff / properties.getZeroSigmaMinDiff()) * properties.getThreshold();
                isAnomalous = true;
            } else {
                z = 0.0;
            }
        }

        return new ZScoreResult(round(z, 4), round(mean, 4), round(stdDev, 4), n, isAnomalous);
    }

    /**
     * Detect anomalies for a service using Z-Score statistical analysis across operational metrics.
     */
    public List<AnomalyEvent> detectAnomalies(
            String service,
            OperationalMetrics currentMetrics,
            List<OperationalMetrics> baselineWindows,
            Instant detectedAt) {

        if (!properties.isEnabled() || currentMetrics == null || currentMetrics.totalEvents() == 0 || baselineWindows == null) {
            return List.of();
        }

        List<AnomalyEvent> anomalies = new ArrayList<>();
        Instant effectiveDetectedAt = detectedAt != null ? detectedAt : Instant.now();

        // 1. Evaluate Error Rate with Z-Score
        List<Double> baselineErrorRates = baselineWindows.stream()
                .filter(w -> w.totalEvents() > 0)
                .map(OperationalMetrics::errorRate)
                .toList();

        double currentErrorRate = currentMetrics.errorRate();
        ZScoreResult errorRateResult = calculateZScore(currentErrorRate, baselineErrorRates);

        if (errorRateResult.isAnomalous() && currentMetrics.errorCount() > 0) {
            AnomalySeverity severity = determineSeverity(errorRateResult.zScore(), currentErrorRate >= 0.50);
            String message = String.format(
                    "Z-Score anomaly detected for '%s' errorRate: current=%.2f%%, zScore=%.2f (threshold=%.2f, mean=%.2f%%, stdDev=%.2f%%)",
                    service, currentErrorRate * 100.0, errorRateResult.zScore(), properties.getThreshold(),
                    errorRateResult.mean() * 100.0, errorRateResult.stdDev() * 100.0
            );

            log.warn("Z-Score Detector: {}", message);

            anomalies.add(new AnomalyEvent(
                    "errorRate",
                    service,
                    round(currentErrorRate, 4),
                    errorRateResult.mean(),
                    errorRateResult.stdDev(),
                    properties.getThreshold(),
                    effectiveDetectedAt,
                    severity,
                    currentMetrics.windowStart(),
                    currentMetrics.windowEnd(),
                    message
            ));
        }

        // 2. Evaluate Latency with Z-Score
        if (currentMetrics.latency() != null && currentMetrics.latency().count() > 0 && currentMetrics.latency().avg() != null) {
            List<Double> baselineLatencies = baselineWindows.stream()
                    .filter(w -> w.latency() != null && w.latency().count() > 0 && w.latency().avg() != null)
                    .map(w -> w.latency().avg())
                    .toList();

            double currentLatency = currentMetrics.latency().avg();
            ZScoreResult latencyResult = calculateZScore(currentLatency, baselineLatencies);

            if (latencyResult.isAnomalous() && currentLatency > latencyResult.mean() + 10.0) {
                AnomalySeverity severity = determineSeverity(latencyResult.zScore(), currentLatency >= latencyResult.mean() * 3.0);
                String message = String.format(
                        "Z-Score anomaly detected for '%s' latencyAvg: current=%.2fms, zScore=%.2f (threshold=%.2f, mean=%.2fms, stdDev=%.2fms)",
                        service, currentLatency, latencyResult.zScore(), properties.getThreshold(),
                        latencyResult.mean(), latencyResult.stdDev()
                );

                log.warn("Z-Score Detector: {}", message);

                anomalies.add(new AnomalyEvent(
                        "latencyAvg",
                        service,
                        round(currentLatency, 2),
                        latencyResult.mean(),
                        latencyResult.stdDev(),
                        properties.getThreshold(),
                        effectiveDetectedAt,
                        severity,
                        currentMetrics.windowStart(),
                        currentMetrics.windowEnd(),
                        message
                ));
            }
        }

        return anomalies;
    }

    public AnomalySeverity determineSeverity(double zScore, boolean isSevereCondition) {
        if (isSevereCondition || zScore >= 5.0) {
            return AnomalySeverity.CRITICAL;
        } else if (zScore >= 4.0) {
            return AnomalySeverity.HIGH;
        } else if (zScore >= 3.0) {
            return AnomalySeverity.MEDIUM;
        }
        return AnomalySeverity.LOW;
    }

    public record ZScoreResult(
            double zScore,
            double mean,
            double stdDev,
            int sampleCount,
            boolean isAnomalous
    ) {
    }

    private static double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }
}
