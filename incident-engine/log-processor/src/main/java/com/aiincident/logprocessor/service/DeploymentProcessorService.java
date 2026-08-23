package com.aiincident.logprocessor.service;

import com.aiincident.logging.deployment.DeploymentEvent;
import com.aiincident.logprocessor.entity.ProcessedDeploymentEvent;
import com.aiincident.logprocessor.repository.DeploymentEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentProcessorService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentProcessorService.class);
    private static final Set<String> VALID_EVENT_TYPES = Set.of("DEPLOYMENT_STARTED", "DEPLOYMENT_COMPLETED");

    private final DeploymentEventRepository deploymentEventRepository;
    private final ObjectMapper objectMapper;

    public DeploymentProcessorService(DeploymentEventRepository deploymentEventRepository, ObjectMapper objectMapper) {
        this.deploymentEventRepository = deploymentEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Process raw JSON string message from Kafka topic deployment-events.
     */
    public Optional<ProcessedDeploymentEvent> processRawMessage(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            log.warn("Rejected deployment event: payload is null or empty");
            return Optional.empty();
        }

        DeploymentEvent event;
        try {
            event = objectMapper.readValue(rawJson, DeploymentEvent.class);
        } catch (Exception e) {
            log.warn("Rejected malformed deployment event JSON: '{}', error: {}", rawJson, e.getMessage());
            return Optional.empty();
        }

        return processEvent(event);
    }

    /**
     * Validate required fields and persist deployment event idempotently.
     */
    @Transactional
    public Optional<ProcessedDeploymentEvent> processEvent(DeploymentEvent event) {
        if (event == null) {
            log.warn("Rejected deployment event: DeploymentEvent object is null");
            return Optional.empty();
        }

        if (!isValid(event)) {
            log.warn("Rejected deployment event due to missing required fields or invalid eventType: eventId='{}', eventType='{}', service='{}', version='{}', timestamp='{}'",
                    event.eventId(), event.eventType(), event.service(), event.version(), event.timestamp());
            return Optional.empty();
        }

        String eventId = event.eventId().trim();

        // 1. Application-level idempotency pre-check
        Optional<ProcessedDeploymentEvent> existing = deploymentEventRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            log.info("Duplicate deployment event delivery detected: eventId='{}', service='{}', eventType='{}'",
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

        ProcessedDeploymentEvent entity = new ProcessedDeploymentEvent(
                eventId,
                event.eventType().trim().toUpperCase(),
                event.service().trim(),
                event.version().trim(),
                event.timestamp(),
                event.traceId() != null ? event.traceId().trim() : null,
                metadataJson,
                Instant.now()
        );

        // 2. Persist with database-level unique constraint protection
        try {
            ProcessedDeploymentEvent saved = deploymentEventRepository.saveAndFlush(entity);
            log.info("Persisted deployment event [id={}, eventId={}, eventType={}, service={}, version={}]",
                    saved.getId(), saved.getEventId(), saved.getEventType(), saved.getService(), saved.getVersion());
            return Optional.of(saved);
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent duplicate deployment event delivery caught by unique constraint: eventId='{}'", eventId);
            return deploymentEventRepository.findByEventId(eventId);
        }
    }

    public boolean isValid(DeploymentEvent event) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            return false;
        }
        if (event.eventType() == null || event.eventType().isBlank() || !VALID_EVENT_TYPES.contains(event.eventType().trim().toUpperCase())) {
            return false;
        }
        if (event.service() == null || event.service().isBlank()) {
            return false;
        }
        if (event.version() == null || event.version().isBlank()) {
            return false;
        }
        if (event.timestamp() == null) {
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<ProcessedDeploymentEvent> findAllDeployments() {
        return deploymentEventRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ProcessedDeploymentEvent> findByService(String service) {
        return deploymentEventRepository.findByServiceOrderByTimestampDesc(service);
    }

    @Transactional(readOnly = true)
    public List<ProcessedDeploymentEvent> findByVersion(String version) {
        return deploymentEventRepository.findByVersion(version);
    }

    @Transactional(readOnly = true)
    public List<ProcessedDeploymentEvent> findByEventType(String eventType) {
        return deploymentEventRepository.findByEventType(eventType);
    }

    @Transactional(readOnly = true)
    public List<ProcessedDeploymentEvent> findByTraceId(String traceId) {
        return deploymentEventRepository.findByTraceId(traceId);
    }
}
