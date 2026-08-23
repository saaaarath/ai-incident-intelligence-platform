package com.aiincident.logprocessor.incident;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
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
        name = "incidents",
        indexes = {
                @Index(name = "idx_incidents_status", columnList = "status"),
                @Index(name = "idx_incidents_service", columnList = "primary_service"),
                @Index(name = "idx_incidents_severity", columnList = "severity"),
                @Index(name = "idx_incidents_started_at", columnList = "started_at")
        }
)
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false, unique = true)
    private String incidentId;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AnomalySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncidentStatus status;

    @Column(name = "primary_service", nullable = false)
    private String primaryService;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "metric")
    private String metric;

    public Incident() {
    }

    public Incident(
            String title,
            AnomalySeverity severity,
            IncidentStatus status,
            String primaryService,
            Instant startedAt,
            Instant detectedAt,
            String description,
            String metric) {
        this.incidentId = UUID.randomUUID().toString();
        this.title = title;
        this.severity = severity != null ? severity : AnomalySeverity.MEDIUM;
        this.status = status != null ? status : IncidentStatus.OPEN;
        this.primaryService = primaryService;
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.detectedAt = detectedAt != null ? detectedAt : Instant.now();
        this.resolvedAt = null;
        this.description = description;
        this.metric = metric;
    }

    public Long getId() {
        return id;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public AnomalySeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AnomalySeverity severity) {
        this.severity = severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public String getPrimaryService() {
        return primaryService;
    }

    public void setPrimaryService(String primaryService) {
        this.primaryService = primaryService;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }
}
