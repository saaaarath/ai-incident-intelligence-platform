package com.aiincident.logprocessor.anomaly;

import com.aiincident.logprocessor.incident.IncidentService;
import com.aiincident.logprocessor.metrics.MetricsAggregationService;
import com.aiincident.logprocessor.metrics.OperationalMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    private final MetricsAggregationService metricsService;
    private final AnomalyRepository anomalyRepository;
    private final AnomalyDetectionProperties properties;
    private final IncidentService incidentService;
    private final ZScoreAnomalyDetector zScoreDetector;

    public AnomalyDetectionService(
            MetricsAggregationService metricsService,
            AnomalyRepository anomalyRepository,
            AnomalyDetectionProperties properties) {
        this(metricsService, anomalyRepository, properties, null, null);
    }

    public AnomalyDetectionService(
            MetricsAggregationService metricsService,
            AnomalyRepository anomalyRepository,
            AnomalyDetectionProperties properties,
            IncidentService incidentService) {
        this(metricsService, anomalyRepository, properties, incidentService, null);
    }

    @Autowired
    public AnomalyDetectionService(
            MetricsAggregationService metricsService,
            AnomalyRepository anomalyRepository,
            AnomalyDetectionProperties properties,
            @Autowired(required = false) IncidentService incidentService,
            @Autowired(required = false) ZScoreAnomalyDetector zScoreDetector) {
        this.metricsService = metricsService;
        this.anomalyRepository = anomalyRepository;
        this.properties = properties;
        this.incidentService = incidentService;
        this.zScoreDetector = (zScoreDetector != null) ? zScoreDetector : new ZScoreAnomalyDetector(new ZScoreProperties());
    }

    /**
     * Detect and persist anomalies across all services for a given time window against historical baseline,
     * and automatically convert them into incidents.
     */
    @Transactional
    public List<AnomalyEvent> detectAndSaveAnomalies(
            Instant currentWindowStart,
            Instant currentWindowEnd,
            Instant baselineStart,
            Instant baselineEnd) {
        List<AnomalyEvent> anomalies = detectAnomalies(currentWindowStart, currentWindowEnd, baselineStart, baselineEnd);
        if (!anomalies.isEmpty()) {
            List<AnomalyEvent> saved = anomalyRepository.saveAll(anomalies);
            if (incidentService != null) {
                incidentService.processAnomalies(saved);
            }
            return saved;
        }
        return List.of();
    }

    /**
     * Detect and persist anomalies for a specific service using specified strategy,
     * and automatically convert them into incidents.
     */
    @Transactional
    public List<AnomalyEvent> detectAndSaveAnomaliesForService(
            String service,
            String strategy,
            Instant currentWindowStart,
            Instant currentWindowEnd,
            Instant baselineStart,
            Instant baselineEnd) {
        List<AnomalyEvent> detected = detectAnomaliesWithStrategy(
                service,
                strategy,
                currentWindowStart,
                currentWindowEnd,
                baselineStart,
                baselineEnd
        );
        if (!detected.isEmpty()) {
            List<AnomalyEvent> saved = anomalyRepository.saveAll(detected);
            if (incidentService != null) {
                incidentService.processAnomalies(saved);
            }
            return saved;
        }
        return List.of();
    }

    /**
     * Detect anomalies for a single service using the Z-Score detector.
     */
    @Transactional(readOnly = true)
    public List<AnomalyEvent> detectZScoreAnomaliesForService(
            String service,
            Instant currentWindowStart,
            Instant currentWindowEnd,
            Instant baselineStart,
            Instant baselineEnd) {
        if (service == null || service.isBlank() || zScoreDetector == null) {
            return List.of();
        }

        OperationalMetrics currentMetrics = metricsService.getSummary(service, currentWindowStart, currentWindowEnd);
        if (currentMetrics.totalEvents() == 0) {
            return List.of();
        }

        Duration windowDuration = Duration.ofMinutes(1);
        List<OperationalMetrics> baselineWindows = metricsService.getMetrics(
                service,
                baselineStart,
                baselineEnd,
                windowDuration
        );

        return zScoreDetector.detectAnomalies(service, currentMetrics, baselineWindows, Instant.now());
    }

    /**
     * Detect anomalies for a service with configurable strategy: "THRESHOLD", "ZSCORE", or "ALL".
     */
    @Transactional(readOnly = true)
    public List<AnomalyEvent> detectAnomaliesWithStrategy(
            String service,
            String strategy,
            Instant currentWindowStart,
            Instant currentWindowEnd,
            Instant baselineStart,
            Instant baselineEnd) {
        if ("ZSCORE".equalsIgnoreCase(strategy)) {
            return detectZScoreAnomaliesForService(service, currentWindowStart, currentWindowEnd, baselineStart, baselineEnd);
        } else if ("ALL".equalsIgnoreCase(strategy) && zScoreDetector != null) {
            List<AnomalyEvent> thresholdAnomalies = detectAnomaliesForService(service, currentWindowStart, currentWindowEnd, baselineStart, baselineEnd);
            List<AnomalyEvent> zScoreAnomalies = detectZScoreAnomaliesForService(service, currentWindowStart, currentWindowEnd, baselineStart, baselineEnd);
            List<AnomalyEvent> combined = new ArrayList<>(thresholdAnomalies);
            for (AnomalyEvent zEvent : zScoreAnomalies) {
                boolean duplicate = combined.stream().anyMatch(e ->
                        e.getService().equals(zEvent.getService()) && e.getMetric().equals(zEvent.getMetric()));
                if (!duplicate) {
                    combined.add(zEvent);
                }
            }
            return combined;
        } else {
            return detectAnomaliesForService(service, currentWindowStart, currentWindowEnd, baselineStart, baselineEnd);
        }
    }

    /**
     * Detect anomalies across all services without saving.
     */
    @Transactional(readOnly = true)
    public List<AnomalyEvent> detectAnomalies(
            Instant currentWindowStart,
            Instant currentWindowEnd,
            Instant baselineStart,
            Instant baselineEnd) {
        List<String> services = metricsService.getServices();
        List<AnomalyEvent> allAnomalies = new ArrayList<>();

        for (String service : services) {
            List<AnomalyEvent> serviceAnomalies = detectAnomaliesForService(
                    service,
                    currentWindowStart,
                    currentWindowEnd,
                    baselineStart,
                    baselineEnd
            );
            allAnomalies.addAll(serviceAnomalies);
        }

        return allAnomalies;
    }

    /**
     * Detect anomalies for a single service comparing current window against baseline period.
     */
    @Transactional(readOnly = true)
    public List<AnomalyEvent> detectAnomaliesForService(
            String service,
            Instant currentWindowStart,
            Instant currentWindowEnd,
            Instant baselineStart,
            Instant baselineEnd) {
        if (service == null || service.isBlank()) {
            return List.of();
        }

        // 1. Calculate current window metrics
        OperationalMetrics currentMetrics = metricsService.getSummary(service, currentWindowStart, currentWindowEnd);
        if (currentMetrics.totalEvents() == 0) {
            // No activity in current window -> nothing to trigger
            return List.of();
        }

        // 2. Calculate baseline windows (1-minute fixed window granular metrics over baseline period)
        Duration windowDuration = Duration.ofMinutes(1);
        List<OperationalMetrics> baselineWindows = metricsService.getMetrics(
                service,
                baselineStart,
                baselineEnd,
                windowDuration
        );

        List<AnomalyEvent> anomalies = new ArrayList<>();
        Instant detectedAt = Instant.now();

        // 3. Evaluate Error Rate Anomaly
        AnomalyEvent errorRateAnomaly = evaluateErrorRateAnomaly(
                service,
                currentMetrics,
                baselineWindows,
                detectedAt
        );
        if (errorRateAnomaly != null) {
            anomalies.add(errorRateAnomaly);
        }

        // 4. Evaluate Latency Anomaly
        AnomalyEvent latencyAnomaly = evaluateLatencyAnomaly(
                service,
                currentMetrics,
                baselineWindows,
                detectedAt
        );
        if (latencyAnomaly != null) {
            anomalies.add(latencyAnomaly);
        }

        return anomalies;
    }

    /**
     * Calculate baseline mean and standard deviation variability for a metric.
     */
    public MetricBaseline calculateBaseline(String metric, String service, List<Double> values) {
        if (values == null || values.isEmpty()) {
            return MetricBaseline.empty(metric, service);
        }

        double sum = 0.0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        for (double v : values) {
            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
        }

        int n = values.size();
        double mean = sum / n;

        double sumSquaredDiff = 0.0;
        for (double v : values) {
            sumSquaredDiff += Math.pow(v - mean, 2);
        }
        double variability = Math.sqrt(sumSquaredDiff / n);

        return new MetricBaseline(
                metric,
                service,
                round(mean, 4),
                round(variability, 4),
                round(min, 4),
                round(max, 4),
                n
        );
    }

    private AnomalyEvent evaluateErrorRateAnomaly(
            String service,
            OperationalMetrics currentMetrics,
            List<OperationalMetrics> baselineWindows,
            Instant detectedAt) {

        List<Double> baselineValues = baselineWindows.stream()
                .filter(w -> w.totalEvents() > 0)
                .map(OperationalMetrics::errorRate)
                .toList();

        MetricBaseline baseline = calculateBaseline("errorRate", service, baselineValues);
        double currentValue = currentMetrics.errorRate();
        double sigmaThreshold = properties.getSigmaThreshold();
        double absoluteThreshold = properties.getErrorRateAbsoluteThreshold();

        double thresholdValue;
        boolean isAnomaly = false;
        double zScore = 0.0;

        if (baseline.variability() > 0.0001) {
            thresholdValue = baseline.mean() + (sigmaThreshold * baseline.variability());
            zScore = (currentValue - baseline.mean()) / baseline.variability();
            // Anomaly if current value exceeds sigma threshold and meets minimal error threshold
            if (currentValue > thresholdValue && currentValue >= absoluteThreshold) {
                isAnomaly = true;
            }
        } else {
            // When baseline variability is 0 (e.g. 0% error rate historically), use absolute threshold
            thresholdValue = baseline.mean() + absoluteThreshold;
            if (currentValue >= thresholdValue && currentMetrics.errorCount() > 0) {
                isAnomaly = true;
                zScore = (currentValue - baseline.mean()) / (absoluteThreshold > 0 ? absoluteThreshold : 0.01);
            }
        }

        if (isAnomaly) {
            AnomalySeverity severity;
            if (currentValue >= 0.50 || zScore >= 5.0) {
                severity = AnomalySeverity.CRITICAL;
            } else if (currentValue >= 0.20 || zScore >= 3.0) {
                severity = AnomalySeverity.HIGH;
            } else if (currentValue >= 0.05 || zScore >= 2.0) {
                severity = AnomalySeverity.MEDIUM;
            } else {
                severity = AnomalySeverity.LOW;
            }

            String message = String.format(
                    "Error rate anomaly detected for service '%s': current=%.2f%% (threshold=%.2f%%, baseline mean=%.2f%%, stdDev=%.2f%%)",
                    service, currentValue * 100.0, thresholdValue * 100.0, baseline.mean() * 100.0, baseline.variability() * 100.0
            );

            log.warn("Anomaly Detected: {}", message);

            return new AnomalyEvent(
                    "errorRate",
                    service,
                    round(currentValue, 4),
                    baseline.mean(),
                    baseline.variability(),
                    round(thresholdValue, 4),
                    detectedAt,
                    severity,
                    currentMetrics.windowStart(),
                    currentMetrics.windowEnd(),
                    message
            );
        }

        return null;
    }

    private AnomalyEvent evaluateLatencyAnomaly(
            String service,
            OperationalMetrics currentMetrics,
            List<OperationalMetrics> baselineWindows,
            Instant detectedAt) {

        if (currentMetrics.latency() == null || currentMetrics.latency().count() == 0 || currentMetrics.latency().avg() == null) {
            return null;
        }

        List<Double> baselineValues = baselineWindows.stream()
                .filter(w -> w.latency() != null && w.latency().count() > 0 && w.latency().avg() != null)
                .map(w -> w.latency().avg())
                .toList();

        if (baselineValues.isEmpty()) {
            return null;
        }

        MetricBaseline baseline = calculateBaseline("latencyAvg", service, baselineValues);
        double currentValue = currentMetrics.latency().avg();
        double sigmaThreshold = properties.getSigmaThreshold();
        double multiplier = properties.getLatencySpikeMultiplier();

        double thresholdValue;
        boolean isAnomaly = false;
        double zScore = 0.0;

        if (baseline.variability() > 1.0) {
            thresholdValue = baseline.mean() + (sigmaThreshold * baseline.variability());
            zScore = (currentValue - baseline.mean()) / baseline.variability();
            if (currentValue > thresholdValue && currentValue > baseline.mean() * 1.25) {
                isAnomaly = true;
            }
        } else {
            // Very low variability baseline (e.g. constant 50ms)
            thresholdValue = baseline.mean() * multiplier;
            if (currentValue >= thresholdValue && currentValue > baseline.mean() + 20.0) {
                isAnomaly = true;
                zScore = (currentValue - baseline.mean()) / (baseline.mean() > 0 ? baseline.mean() : 1.0);
            }
        }

        if (isAnomaly) {
            AnomalySeverity severity;
            if (currentValue >= baseline.mean() * 3.0 || zScore >= 5.0) {
                severity = AnomalySeverity.CRITICAL;
            } else if (currentValue >= baseline.mean() * 2.0 || zScore >= 3.0) {
                severity = AnomalySeverity.HIGH;
            } else {
                severity = AnomalySeverity.MEDIUM;
            }

            String message = String.format(
                    "Latency anomaly detected for service '%s': current=%.2fms (threshold=%.2fms, baseline mean=%.2fms, stdDev=%.2fms)",
                    service, currentValue, thresholdValue, baseline.mean(), baseline.variability()
            );

            log.warn("Anomaly Detected: {}", message);

            return new AnomalyEvent(
                    "latencyAvg",
                    service,
                    round(currentValue, 2),
                    baseline.mean(),
                    baseline.variability(),
                    round(thresholdValue, 2),
                    detectedAt,
                    severity,
                    currentMetrics.windowStart(),
                    currentMetrics.windowEnd(),
                    message
            );
        }

        return null;
    }

    private static double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }
}
