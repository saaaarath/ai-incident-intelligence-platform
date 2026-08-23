package com.aiincident.logprocessor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "deployment_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dep_events_event_id", columnNames = {"event_id"})
        },
        indexes = {
                @Index(name = "idx_dep_events_service", columnList = "service"),
                @Index(name = "idx_dep_events_event_type", columnList = "event_type"),
                @Index(name = "idx_dep_events_version", columnList = "version"),
                @Index(name = "idx_dep_events_timestamp", columnList = "timestamp")
        }
)
public class ProcessedDeploymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "service", nullable = false)
    private String service;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public ProcessedDeploymentEvent() {
    }

    public ProcessedDeploymentEvent(
            String eventId,
            String eventType,
            String service,
            String version,
            Instant timestamp,
            String traceId,
            String metadata,
            Instant receivedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.service = service;
        this.version = version;
        this.timestamp = timestamp;
        this.traceId = traceId;
        this.metadata = metadata;
        this.receivedAt = receivedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
