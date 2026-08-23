package com.aiincident.logprocessor.service;

import com.aiincident.logging.LogEvent;
import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.incident.IncidentCorrelationService;
import com.aiincident.logprocessor.incident.IncidentProperties;
import com.aiincident.logprocessor.repository.LogEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogProcessorService {

    private static final Logger log = LoggerFactory.getLogger(LogProcessorService.class);

    private final LogEventRepository logEventRepository;
    private final ObjectMapper objectMapper;
    private final IncidentCorrelationService correlationService;
    private final IncidentProperties incidentProperties;

    public LogProcessorService(LogEventRepository logEventRepository, ObjectMapper objectMapper) {
        this(logEventRepository, objectMapper, null, null);
    }

    @Autowired
    public LogProcessorService(
            LogEventRepository logEventRepository,
            ObjectMapper objectMapper,
            @Autowired(required = false) IncidentCorrelationService correlationService,
            @Autowired(required = false) IncidentProperties incidentProperties) {
        this.logEventRepository = logEventRepository;
        this.objectMapper = objectMapper;
        this.correlationService = correlationService;
        this.incidentProperties = incidentProperties;
    }

    /**
     * Process raw JSON string message from Kafka. Safely handles malformed JSON and missing fields.
     */
    public Optional<ProcessedLogEvent> processRawMessage(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            log.warn("Rejected log event: payload is null or empty");
            return Optional.empty();
        }

        LogEvent event;
        try {
            event = objectMapper.readValue(rawJson, LogEvent.class);
        } catch (Exception e) {
            log.warn("Rejected malformed log event JSON: '{}', error: {}", rawJson, e.getMessage());
            return Optional.empty();
        }

        return processEvent(event);
    }

    /**
     * Validate required fields and persist valid log event idempotently.
     */
    @Transactional
    public Optional<ProcessedLogEvent> processEvent(LogEvent event) {
        if (event == null) {
            log.warn("Rejected log event: LogEvent object is null");
            return Optional.empty();
        }

        if (!isValid(event)) {
            log.warn("Rejected log event due to missing required fields: eventId='{}', timestamp='{}', service='{}', level='{}', eventType='{}', traceId='{}', message='{}'",
                    event.eventId(), event.timestamp(), event.service(), event.level(), event.eventType(), event.traceId(), event.message());
            return Optional.empty();
        }

        String eventId = event.eventId().trim();

        // 1. Application-level idempotency pre-check
        Optional<ProcessedLogEvent> existing = logEventRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            log.info("Duplicate event delivery detected (already persisted): eventId='{}', service='{}', eventType='{}'",
                    eventId, event.service(), event.eventType());
            return existing;
        }

        String metadataJson = null;
        if (event.metadata() != null && !event.metadata().isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(event.metadata());
            } catch (JsonProcessingException e) {
                metadataJson = "{}";
            }
        }

        ProcessedLogEvent entity = new ProcessedLogEvent(
                eventId,
                event.timestamp(),
                event.service().trim(),
                event.level().trim(),
                event.eventType().trim(),
                event.traceId().trim(),
                event.message().trim(),
                metadataJson,
                Instant.now()
        );

        // 2. Persist with database-level unique constraint protection against race conditions
        ProcessedLogEvent saved;
        try {
            saved = logEventRepository.saveAndFlush(entity);
            log.info("Persisted log event [id={}, eventId={}, service={}, eventType={}, traceId={}]",
                    saved.getId(), saved.getEventId(), saved.getService(), saved.getEventType(), saved.getTraceId());
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent duplicate event delivery caught by unique constraint: eventId='{}'", eventId);
            return logEventRepository.findByEventId(eventId);
        }

        // 3. Auto-correlate into incident if enabled and event is a failure event
        if (correlationService != null && (incidentProperties == null || incidentProperties.isAutoCorrelateEvents())) {
            try {
                correlationService.correlateLogEvent(saved);
            } catch (Exception e) {
                log.error("Error correlating log event id={}: {}", saved.getId(), e.getMessage(), e);
            }
        }

        return Optional.of(saved);
    }

    public boolean isValid(LogEvent event) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            return false;
        }
        if (event.timestamp() == null) {
            return false;
        }
        if (event.service() == null || event.service().isBlank()) {
            return false;
        }
        if (event.level() == null || event.level().isBlank()) {
            return false;
        }
        if (event.eventType() == null || event.eventType().isBlank()) {
            return false;
        }
        if (event.traceId() == null || event.traceId().isBlank()) {
            return false;
        }
        if (event.message() == null || event.message().isBlank()) {
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<ProcessedLogEvent> findAllLogs() {
        return logEventRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ProcessedLogEvent> findByTraceId(String traceId) {
        return logEventRepository.findByTraceId(traceId);
    }

    @Transactional(readOnly = true)
    public List<ProcessedLogEvent> findByService(String service) {
        return logEventRepository.findByService(service);
    }

    @Transactional(readOnly = true)
    public List<ProcessedLogEvent> findByEventType(String eventType) {
        return logEventRepository.findByEventType(eventType);
    }
}
