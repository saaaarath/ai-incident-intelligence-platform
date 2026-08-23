package com.aiincident.logprocessor.incident;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.repository.LogEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic Incident Correlation Engine.
 * Correlates cascading and multi-service operational events into unified incidents
 * using time proximity, service topology, event types, and active incident state without AI.
 */
@Service
public class IncidentCorrelationService {

    private static final Logger log = LoggerFactory.getLogger(IncidentCorrelationService.class);

    private static final Set<IncidentStatus> ACTIVE_STATUSES = Set.of(
            IncidentStatus.OPEN,
            IncidentStatus.INVESTIGATING
    );

    private final IncidentRepository incidentRepository;
    private final IncidentEvidenceRepository evidenceRepository;
    private final LogEventRepository logEventRepository;
    private final IncidentProperties properties;
    private final ServiceDependencyGraph dependencyGraph;
    private final EventTypeClassifier eventTypeClassifier;

    public IncidentCorrelationService(
            IncidentRepository incidentRepository,
            IncidentEvidenceRepository evidenceRepository,
            LogEventRepository logEventRepository,
            IncidentProperties properties,
            ServiceDependencyGraph dependencyGraph,
            EventTypeClassifier eventTypeClassifier) {
        this.incidentRepository = incidentRepository;
        this.evidenceRepository = evidenceRepository;
        this.logEventRepository = logEventRepository;
        this.properties = properties;
        this.dependencyGraph = dependencyGraph;
        this.eventTypeClassifier = eventTypeClassifier;
    }

    /**
     * Correlate an incoming processed log event into an existing active incident or create a new incident.
     */
    @Transactional
    public Optional<Incident> correlateLogEvent(ProcessedLogEvent event) {
        if (event == null) {
            return Optional.empty();
        }

        // Only correlate failure/error events
        if (!eventTypeClassifier.isFailureEvent(event.getLevel(), event.getEventType(), event.getMessage())) {
            log.debug("Event {} is not a failure event, skipping correlation", event.getEventId());
            return Optional.empty();
        }

        String service = event.getService() != null ? event.getService().trim() : "unknown-service";
        Instant eventTime = event.getTimestamp() != null ? event.getTimestamp() : Instant.now();
        AnomalySeverity severity = eventTypeClassifier.classifySeverity(event.getLevel(), event.getEventType(), event.getMessage());

        // 1. Search for matching active incident
        Optional<Incident> matchingIncidentOpt = findMatchingActiveIncident(service, eventTime, event.getEventType(), event.getTraceId());

        Incident incident;
        if (matchingIncidentOpt.isPresent()) {
            incident = matchingIncidentOpt.get();
            log.info("Correlating event [{}:{}:{}] to active incident #{} (primary={}, root={})",
                    service, event.getEventType(), eventTime, incident.getId(), incident.getPrimaryService(), incident.getRootService());

            // Upgrade severity if this event is more severe
            if (isHigherSeverity(severity, incident.getSeverity())) {
                log.info("Upgrading incident #{} severity from {} to {} based on event {}",
                        incident.getId(), incident.getSeverity(), severity, event.getEventType());
                incident.setSeverity(severity);
            }

            // Track affected service
            incident.addAffectedService(service);

            // Update time tracking
            if (eventTime.isAfter(incident.getLastEventAt())) {
                incident.setLastEventAt(eventTime);
            }

            // Update description with cascading info if new service affected
            if (!incident.getPrimaryService().equalsIgnoreCase(service) &&
                    (incident.getDescription() == null || !incident.getDescription().contains(service))) {
                incident.setDescription(String.format("%s (Cascaded to %s via %s)",
                        incident.getDescription() != null ? incident.getDescription() : incident.getTitle(),
                        service,
                        event.getEventType()));
            }

            incident = incidentRepository.save(incident);
        } else {
            // 2. Create new Incident
            String title = buildTitle(service, event.getEventType(), event.getMessage());
            incident = new Incident(
                    title,
                    severity,
                    IncidentStatus.OPEN,
                    service,
                    eventTime,
                    Instant.now(),
                    event.getMessage(),
                    event.getEventType()
            );
            incident.setLastEventAt(eventTime);
            incident.setRootService(service);
            incident.addAffectedService(service);

            incident = incidentRepository.save(incident);
            log.warn("Created new incident #{} [title='{}', severity={}, primaryService='{}'] for event {}",
                    incident.getId(), incident.getTitle(), incident.getSeverity(), incident.getPrimaryService(), event.getEventType());
        }

        // 3. Persist correlated evidence
        IncidentEvidence evidence = new IncidentEvidence(
                incident.getId(),
                event.getEventId(),
                eventTime,
                service,
                event.getEventType(),
                severity,
                event.getMessage(),
                event.getTraceId(),
                event.getMetadata()
        );
        evidenceRepository.save(evidence);
        incident.addEvidence(evidence);

        return Optional.of(incident);
    }

    /**
     * Batch correlate a collection of log events in chronological order.
     */
    @Transactional
    public List<Incident> correlateEvents(List<ProcessedLogEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        // Sort events chronologically to preserve cascade order
        List<ProcessedLogEvent> sortedEvents = new ArrayList<>(events);
        sortedEvents.sort(Comparator.comparing(ProcessedLogEvent::getTimestamp));

        Set<Incident> affectedIncidents = new LinkedHashSet<>();
        for (ProcessedLogEvent event : sortedEvents) {
            correlateLogEvent(event).ifPresent(affectedIncidents::add);
        }

        // Populate attached evidence for each incident
        for (Incident inc : affectedIncidents) {
            List<IncidentEvidence> evList = evidenceRepository.findByIncidentIdOrderByTimestampAsc(inc.getId());
            inc.setEvidence(evList);
        }

        return new ArrayList<>(affectedIncidents);
    }

    /**
     * Correlate events within a specific time window.
     */
    @Transactional
    public List<Incident> correlateTimeRange(Instant from, Instant to) {
        Instant startTime = (from != null) ? from : Instant.now().minus(Duration.ofMinutes(15));
        Instant endTime = (to != null) ? to : Instant.now();

        List<ProcessedLogEvent> logs = logEventRepository.findByTimestampBetweenOrderByTimestampAsc(startTime, endTime);
        return correlateEvents(logs);
    }

    /**
     * Find an active incident matching time proximity, service relationship, and event compatibility.
     */
    private Optional<Incident> findMatchingActiveIncident(String service, Instant eventTime, String eventType, String traceId) {
        List<Incident> activeIncidents = incidentRepository.findByStatusIn(ACTIVE_STATUSES);
        if (activeIncidents.isEmpty()) {
            return Optional.empty();
        }

        int correlationWindowSec = properties.getCorrelationWindowSeconds();
        int maxIncidentWindowMin = properties.getMaxIncidentWindowMinutes();

        for (Incident active : activeIncidents) {
            // 1. Check Time Proximity:
            // Event must be within correlationWindowSeconds of last event or startedAt,
            // and within maxIncidentWindowMinutes from incident startedAt
            Instant incidentStart = active.getStartedAt();
            Instant incidentLast = active.getLastEventAt();

            long secondsFromLast = Math.abs(Duration.between(incidentLast, eventTime).toSeconds());
            long minutesFromStart = Math.abs(Duration.between(incidentStart, eventTime).toMinutes());

            boolean timeMatch = (secondsFromLast <= correlationWindowSec || minutesFromStart <= properties.getActiveWindowMinutes())
                    && (minutesFromStart <= maxIncidentWindowMin);

            if (!timeMatch) {
                continue;
            }

            // 2. Check Trace ID match (Highest confidence correlation)
            if (traceId != null && !traceId.isBlank()) {
                List<IncidentEvidence> existingTraceMatches = evidenceRepository.findByTraceId(traceId.trim());
                boolean sharesTrace = existingTraceMatches.stream()
                        .anyMatch(ev -> ev.getIncidentId().equals(active.getId()));
                if (sharesTrace) {
                    return Optional.of(active);
                }
            }

            // 3. Check Service Proximity:
            // a) Same primary service
            if (active.getPrimaryService().equalsIgnoreCase(service)) {
                return Optional.of(active);
            }

            // b) Service already affected in this incident
            if (active.getAffectedServices().stream().anyMatch(s -> s.equalsIgnoreCase(service))) {
                return Optional.of(active);
            }

            // c) Dependent / Topological service relation
            if (properties.isCrossServiceCorrelationEnabled() &&
                    dependencyGraph.areServicesRelated(active.getPrimaryService(), service)) {
                return Optional.of(active);
            }
        }

        return Optional.empty();
    }

    private String buildTitle(String service, String eventType, String message) {
        if ("DB_TIMEOUT".equalsIgnoreCase(eventType) || "DATABASE_TIMEOUT".equalsIgnoreCase(eventType)) {
            return String.format("Database Timeout Incident on %s", service);
        } else if ("POOL_EXHAUSTED".equalsIgnoreCase(eventType) || "CONNECTION_POOL_EXHAUSTED".equalsIgnoreCase(eventType)) {
            return String.format("Connection Pool Exhaustion on %s", service);
        } else if ("PAYMENT_FAILED".equalsIgnoreCase(eventType) || "PAYMENT_FAILURE".equalsIgnoreCase(eventType)) {
            return String.format("Payment Processing Failure on %s", service);
        } else if ("ORDER_TIMEOUT".equalsIgnoreCase(eventType) || "ORDER_FAILED".equalsIgnoreCase(eventType)) {
            return String.format("Order Service Timeout Failure on %s", service);
        } else if ("SERVICE_UNAVAILABLE".equalsIgnoreCase(eventType)) {
            return String.format("Service Outage on %s", service);
        } else if (eventType != null && !eventType.isBlank()) {
            return String.format("Operational Incident (%s) on %s", eventType, service);
        }
        return String.format("Service Incident on %s", service);
    }

    private boolean isHigherSeverity(AnomalySeverity candidate, AnomalySeverity current) {
        if (candidate == null) return false;
        if (current == null) return true;
        return candidate.ordinal() > current.ordinal();
    }
}
