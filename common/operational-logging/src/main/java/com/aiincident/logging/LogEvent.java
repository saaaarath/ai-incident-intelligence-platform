package com.aiincident.logging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LogEvent(
        String eventId,
        Instant timestamp,
        String service,
        String level,
        String eventType,
        String traceId,
        String message,
        Map<String, Object> metadata) {

    public LogEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static LogEvent create(
            String service,
            String level,
            String eventType,
            String traceId,
            String message,
            Map<String, Object> metadata) {
        return new LogEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                service,
                level,
                eventType,
                traceId,
                message,
                metadata);
    }
}