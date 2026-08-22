package com.aiincident.logging;

import com.aiincident.logging.trace.TraceConstants;
import com.aiincident.logging.trace.TraceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;

public final class StructuredLogger {

    private static volatile LogEventPublisher defaultPublisher;

    private final Logger logger;
    private final ObjectMapper objectMapper;
    private final String service;
    private final LogEventPublisher publisher;

    public static void setDefaultPublisher(LogEventPublisher publisher) {
        defaultPublisher = publisher;
    }

    public static LogEventPublisher getDefaultPublisher() {
        return defaultPublisher;
    }

    public StructuredLogger(Logger logger, ObjectMapper objectMapper, String service) {
        this(logger, objectMapper, service, null);
    }

    public StructuredLogger(Logger logger, ObjectMapper objectMapper, String service, LogEventPublisher publisher) {
        this.logger = logger;
        this.objectMapper = objectMapper;
        this.service = service;
        this.publisher = publisher;
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

        publish(event, json);
    }

    private void publish(LogEvent event, String json) {
        LogEventPublisher pub = (publisher != null) ? publisher : defaultPublisher;
        if (pub != null) {
            try {
                pub.publish(event, json);
            } catch (Exception ignored) {
                // Publishing must never throw or disrupt application flow
            }
        }
    }

    private String traceId() {
        String traceId = TraceContext.getTraceId();
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