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
        name = "incident_evidence",
        indexes = {
                @Index(name = "idx_evidence_incident_id", columnList = "incident_id"),
                @Index(name = "idx_evidence_event_id", columnList = "event_id"),
                @Index(name = "idx_evidence_timestamp", columnList = "timestamp"),
                @Index(name = "idx_evidence_service", columnList = "service"),
                @Index(name = "idx_evidence_trace_id", columnList = "trace_id")
        }
)
public class IncidentEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "service", nullable = false)
    private String service;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AnomalySeverity severity;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "correlated_at", nullable = false)
    private Instant correlatedAt;

    public IncidentEvidence() {
    }

    public IncidentEvidence(
            Long incidentId,
            String eventId,
            Instant timestamp,
            String service,
            String eventType,
            AnomalySeverity severity,
            String message,
            String traceId,
            String metadataJson) {
        this.incidentId = incidentId;
        this.eventId = eventId != null ? eventId : UUID.randomUUID().toString();
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.service = service;
        this.eventType = eventType;
        this.severity = severity != null ? severity : AnomalySeverity.MEDIUM;
        this.message = message;
        this.traceId = traceId;
        this.metadataJson = metadataJson;
        this.correlatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public AnomalySeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AnomalySeverity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public Instant getCorrelatedAt() {
        return correlatedAt;
    }

    public void setCorrelatedAt(Instant correlatedAt) {
        this.correlatedAt = correlatedAt;
    }
}
