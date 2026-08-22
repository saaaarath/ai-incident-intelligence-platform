package com.aiincident.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.MDC;

public final class StructuredLogger {

    private final Logger logger;
    private final ObjectMapper objectMapper;
    private final String service;

    public StructuredLogger(Logger logger, ObjectMapper objectMapper, String service) {
        this.logger = logger;
        this.objectMapper = objectMapper;
        this.service = service;
    }

    public void info(String eventType, String message, Map<String, Object> metadata) {
        write("INFO", eventType, message, metadata, null);
    }

    public void warn(String eventType, String message, Map<String, Object> metadata) {
        write("WARN", eventType, message, metadata, null);
    }

    public void error(String eventType, String message, Map<String, Object> metadata, Throwable cause) {
        write("ERROR", eventType, message, metadata, cause);
    }

    private void write(
            String level,
            String eventType,
            String message,
            Map<String, Object> metadata,
            Throwable cause) {
        Map<String, Object> eventMetadata = new HashMap<>(metadata == null ? Map.of() : metadata);
        if (cause != null) {
            eventMetadata.put("exceptionType", cause.getClass().getName());
            eventMetadata.put("exceptionMessage", cause.getMessage());
        }
        LogEvent event = LogEvent.create(
                service,
                level,
                eventType,
                traceId(),
                message,
                eventMetadata);
        String json = serialize(event);
        if ("ERROR".equals(level)) {
            logger.error(json, cause);
        } else if ("WARN".equals(level)) {
            logger.warn(json);
        } else {
            logger.info(json);
        }
    }

    private String traceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    private String serialize(LogEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize operational log event", exception);
        }
    }
}