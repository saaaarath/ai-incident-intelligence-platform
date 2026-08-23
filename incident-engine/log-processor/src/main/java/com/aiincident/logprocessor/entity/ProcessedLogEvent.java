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
        name = "application_logs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_app_logs_event_id", columnNames = {"event_id"})
        },
        indexes = {
                @Index(name = "idx_app_logs_trace_id", columnList = "trace_id"),
                @Index(name = "idx_app_logs_service", columnList = "service"),
                @Index(name = "idx_app_logs_event_type", columnList = "event_type"),
                @Index(name = "idx_app_logs_timestamp", columnList = "timestamp")
        }
)
public class ProcessedLogEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "service", nullable = false)
    private String service;

    @Column(name = "level", nullable = false)
    private String level;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "trace_id", nullable = false)
    private String traceId;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public ProcessedLogEvent() {
    }

    public ProcessedLogEvent(
            String eventId,
            Instant timestamp,
            String service,
            String level,
            String eventType,
            String traceId,
            String message,
            String metadata,
            Instant receivedAt) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.service = service;
        this.level = level;
        this.eventType = eventType;
        this.traceId = traceId;
        this.message = message;
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

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
