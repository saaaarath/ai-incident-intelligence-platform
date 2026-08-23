package com.aiincident.logprocessor.anomaly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "detected_anomalies",
        indexes = {
                @Index(name = "idx_anomalies_service", columnList = "service"),
                @Index(name = "idx_anomalies_metric", columnList = "metric"),
                @Index(name = "idx_anomalies_detected_at", columnList = "detected_at"),
                @Index(name = "idx_anomalies_severity", columnList = "severity")
        }
)
public class AnomalyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anomaly_id", nullable = false, unique = true)
    private String anomalyId;

    @Column(name = "metric", nullable = false)
    private String metric;

    @Column(name = "service", nullable = false)
    private String service;

    @Column(name = "current_value", nullable = false)
    private Double currentValue;

    @Column(name = "baseline_mean", nullable = false)
    private Double baselineMean;

    @Column(name = "baseline_variability", nullable = false)
    private Double baselineVariability;

    @Column(name = "threshold", nullable = false)
    private Double threshold;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AnomalySeverity severity;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "window_end")
    private Instant windowEnd;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    public AnomalyEvent() {
    }

    public AnomalyEvent(
            String metric,
            String service,
            Double currentValue,
            Double baselineMean,
            Double baselineVariability,
            Double threshold,
            Instant detectedAt,
            AnomalySeverity severity,
            Instant windowStart,
            Instant windowEnd,
            String message) {
        this.anomalyId = UUID.randomUUID().toString();
        this.metric = metric;
        this.service = service;
        this.currentValue = currentValue;
        this.baselineMean = baselineMean;
        this.baselineVariability = baselineVariability;
        this.threshold = threshold;
        this.detectedAt = detectedAt != null ? detectedAt : Instant.now();
        this.severity = severity != null ? severity : AnomalySeverity.MEDIUM;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public String getAnomalyId() {
        return anomalyId;
    }

    public void setAnomalyId(String anomalyId) {
        this.anomalyId = anomalyId;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public Double getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Double currentValue) {
        this.currentValue = currentValue;
    }

    public Double getBaselineMean() {
        return baselineMean;
    }

    public void setBaselineMean(Double baselineMean) {
        this.baselineMean = baselineMean;
    }

    public Double getBaselineVariability() {
        return baselineVariability;
    }

    public void setBaselineVariability(Double baselineVariability) {
        this.baselineVariability = baselineVariability;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public AnomalySeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AnomalySeverity severity) {
        this.severity = severity;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Instant windowEnd) {
        this.windowEnd = windowEnd;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
