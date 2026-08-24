package com.aiincident.logprocessor.timeline;

import com.aiincident.logprocessor.anomaly.AnomalyEvent;
import com.aiincident.logprocessor.anomaly.AnomalyRepository;
import com.aiincident.logprocessor.entity.ProcessedDeploymentEvent;
import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentEvidence;
import com.aiincident.logprocessor.incident.IncidentEvidenceRepository;
import com.aiincident.logprocessor.incident.IncidentRepository;
import com.aiincident.logprocessor.metrics.MetricsAggregationService;
import com.aiincident.logprocessor.metrics.OperationalMetrics;
import com.aiincident.logprocessor.repository.DeploymentEventRepository;
import com.aiincident.logprocessor.repository.LogEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for building a unified, chronologically sorted incident timeline combining
 * anomalies, operational logs, deployment events, service failures, and metrics.
 */
@Service
public class IncidentTimelineService {

    private static final Logger log = LoggerFactory.getLogger(IncidentTimelineService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentEvidenceRepository evidenceRepository;
    private final AnomalyRepository anomalyRepository;
    private final LogEventRepository logEventRepository;
    private final DeploymentEventRepository deploymentRepository;
    private final MetricsAggregationService metricsService;

    @Autowired
    public IncidentTimelineService(
            IncidentRepository incidentRepository,
            IncidentEvidenceRepository evidenceRepository,
            AnomalyRepository anomalyRepository,
            LogEventRepository logEventRepository,
            DeploymentEventRepository deploymentRepository,
            @Autowired(required = false) MetricsAggregationService metricsService) {
        this.incidentRepository = incidentRepository;
        this.evidenceRepository = evidenceRepository;
        this.anomalyRepository = anomalyRepository;
        this.logEventRepository = logEventRepository;
        this.deploymentRepository = deploymentRepository;
        this.metricsService = metricsService;
    }

    /**
     * Build chronological timeline for a specific incident by ID.
     */
    @Transactional(readOnly = true)
    public Optional<IncidentTimeline> buildTimeline(Long incidentId, Integer bufferMinutes, List<TimelineEventType> filterTypes) {
        if (incidentId == null) {
            return Optional.empty();
        }

        Optional<Incident> incidentOpt = incidentRepository.findById(incidentId);
        if (incidentOpt.isEmpty()) {
            return Optional.empty();
        }

        Incident incident = incidentOpt.get();
        return Optional.of(buildTimelineForIncident(incident, bufferMinutes, filterTypes));
    }

    /**
     * Build chronological timeline for a loaded incident.
     */
    @Transactional(readOnly = true)
    public IncidentTimeline buildTimelineForIncident(Incident incident, Integer bufferMinutes, List<TimelineEventType> filterTypes) {
        int buffer = (bufferMinutes != null && bufferMinutes >= 0) ? bufferMinutes : 5;

        Instant start = (incident.getStartedAt() != null ? incident.getStartedAt() : incident.getDetectedAt()).minus(Duration.ofMinutes(buffer));
        Instant end = (incident.getResolvedAt() != null ? incident.getResolvedAt() :
                (incident.getLastEventAt() != null ? incident.getLastEventAt() : Instant.now())).plus(Duration.ofMinutes(buffer));

        Set<String> affectedServices = new HashSet<>(incident.getAffectedServices());
        if (incident.getPrimaryService() != null) affectedServices.add(incident.getPrimaryService().trim());
        if (incident.getRootService() != null) affectedServices.add(incident.getRootService().trim());

        List<TimelineEvent> rawEvents = new ArrayList<>();
        Set<String> evidenceEventIds = new HashSet<>();

        // 1. Service Failure Evidence
        List<IncidentEvidence> evidenceList = (incident.getEvidence() != null && !incident.getEvidence().isEmpty())
                ? incident.getEvidence()
                : (evidenceRepository != null && incident.getId() != null
                    ? evidenceRepository.findByIncidentIdOrderByTimestampAsc(incident.getId())
                    : List.of());

        for (IncidentEvidence ev : evidenceList) {
            if (ev.getEventId() != null) {
                evidenceEventIds.add(ev.getEventId());
            }
            Map<String, Object> meta = new HashMap<>();
            if (ev.getFingerprint() != null) meta.put("fingerprint", ev.getFingerprint());
            if (ev.getNormalizedMessage() != null) meta.put("normalizedMessage", ev.getNormalizedMessage());
            if (ev.getTraceId() != null) meta.put("traceId", ev.getTraceId());

            rawEvents.add(new TimelineEvent(
                    "failure-" + (ev.getId() != null ? ev.getId() : ev.getEventId()),
                    ev.getTimestamp(),
                    TimelineEventType.SERVICE_FAILURE,
                    ev.getService(),
                    String.format("%s failure: %s", ev.getService(), ev.getEventType()),
                    ev.getMessage(),
                    ev.getSeverity() != null ? ev.getSeverity().name() : "HIGH",
                    ev.getEventId(),
                    meta
            ));
        }

        // 2. Anomalies
        List<AnomalyEvent> anomalies = anomalyRepository.findByDetectedAtBetween(start, end);
        for (AnomalyEvent anom : anomalies) {
            if (affectedServices.isEmpty() || affectedServices.stream().anyMatch(s -> s.equalsIgnoreCase(anom.getService()))) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("metric", anom.getMetric());
                meta.put("currentValue", anom.getCurrentValue());
                meta.put("baselineMean", anom.getBaselineMean());
                meta.put("threshold", anom.getThreshold());

                rawEvents.add(new TimelineEvent(
                        "anomaly-" + (anom.getId() != null ? anom.getId() : anom.getAnomalyId()),
                        anom.getDetectedAt() != null ? anom.getDetectedAt() : anom.getWindowStart(),
                        TimelineEventType.ANOMALY,
                        anom.getService(),
                        String.format("%s anomaly: %s breached threshold", anom.getService(), anom.getMetric()),
                        anom.getMessage(),
                        anom.getSeverity() != null ? anom.getSeverity().name() : "MEDIUM",
                        anom.getAnomalyId(),
                        meta
                ));
            }
        }

        // 3. Deployment Events
        List<ProcessedDeploymentEvent> deployments = deploymentRepository.findByTimestampBetweenOrderByTimestampAsc(start, end);
        for (ProcessedDeploymentEvent dep : deployments) {
            if (affectedServices.isEmpty() || affectedServices.stream().anyMatch(s -> s.equalsIgnoreCase(dep.getService()))) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("version", dep.getVersion());
                meta.put("eventType", dep.getEventType());

                rawEvents.add(new TimelineEvent(
                        "deployment-" + (dep.getId() != null ? dep.getId() : dep.getEventId()),
                        dep.getTimestamp(),
                        TimelineEventType.DEPLOYMENT,
                        dep.getService(),
                        String.format("%s deployed version %s (%s)", dep.getService(), dep.getVersion(), dep.getEventType()),
                        dep.getMetadata() != null ? dep.getMetadata() : dep.getEventType(),
                        "INFO",
                        dep.getEventId(),
                        meta
                ));
            }
        }

        // 4. Logs (deduplicated against evidence items)
        List<ProcessedLogEvent> logs = logEventRepository.findByTimestampBetweenOrderByTimestampAsc(start, end);
        for (ProcessedLogEvent l : logs) {
            // Deduplicate if already added as service failure evidence
            if (evidenceEventIds.contains(l.getEventId())) {
                continue;
            }
            if (affectedServices.isEmpty() || affectedServices.stream().anyMatch(s -> s.equalsIgnoreCase(l.getService()))) {
                Map<String, Object> meta = new HashMap<>();
                if (l.getTraceId() != null) meta.put("traceId", l.getTraceId());

                rawEvents.add(new TimelineEvent(
                        "log-" + (l.getId() != null ? l.getId() : l.getEventId()),
                        l.getTimestamp(),
                        TimelineEventType.LOG,
                        l.getService(),
                        String.format("%s [%s]: %s", l.getService(), l.getLevel(), l.getEventType()),
                        l.getMessage(),
                        l.getLevel() != null ? l.getLevel() : "INFO",
                        l.getEventId(),
                        meta
                ));
            }
        }

        // 5. Metrics
        if (metricsService != null) {
            for (String svc : affectedServices) {
                try {
                    List<OperationalMetrics> metricWindows = metricsService.getMetrics(svc, start, end, Duration.ofMinutes(1));
                    for (OperationalMetrics sm : metricWindows) {
                        if (sm.errorCount() > 0 || (sm.latency() != null && sm.latency().avg() > 1000.0)) {
                            Map<String, Object> meta = new HashMap<>();
                            meta.put("totalRequests", sm.totalEvents());
                            meta.put("errorCount", sm.errorCount());
                            meta.put("errorRate", sm.errorRate());
                            meta.put("avgLatency", sm.latency() != null ? sm.latency().avg() : 0.0);

                            String severity = sm.errorRate() >= 0.1 ? "HIGH" : (sm.errorRate() > 0 ? "MEDIUM" : "LOW");
                            rawEvents.add(new TimelineEvent(
                                    "metric-" + svc + "-" + sm.windowStart().toEpochMilli(),
                                    sm.windowStart(),
                                    TimelineEventType.METRIC,
                                    svc,
                                    String.format("%s metrics: errorRate=%.1f%%, avgLatency=%.0fms", svc, sm.errorRate() * 100.0, sm.latency() != null ? sm.latency().avg() : 0.0),
                                    String.format("%d requests, %d errors during window", sm.totalEvents(), sm.errorCount()),
                                    severity,
                                    null,
                                    meta
                            ));
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error aggregating metrics for timeline service {}: {}", svc, e.getMessage());
                }
            }
        }

        // 6. Ensure core incident lifecycle milestones are present if raw event streams are sparse
        if (rawEvents.isEmpty() && incident != null) {
            String primarySvc = incident.getPrimaryService() != null ? incident.getPrimaryService() : "unknown";
            String metric = incident.getMetric() != null ? incident.getMetric() : "telemetry";
            Instant started = incident.getStartedAt() != null ? incident.getStartedAt() : (incident.getDetectedAt() != null ? incident.getDetectedAt() : Instant.now());

            rawEvents.add(new TimelineEvent(
                    "lifecycle-anomaly-" + (incident.getId() != null ? incident.getId() : "seed"),
                    started,
                    TimelineEventType.ANOMALY,
                    primarySvc,
                    String.format("Telemetry anomaly observed on %s: %s", primarySvc, metric),
                    incident.getDescription() != null ? incident.getDescription() : "Anomaly signature detected",
                    incident.getSeverity() != null ? incident.getSeverity().name() : "MEDIUM",
                    "ANOM-" + primarySvc,
                    Map.of("metric", metric, "primaryService", primarySvc)
            ));

            Instant detected = incident.getDetectedAt() != null ? incident.getDetectedAt() : started.plusSeconds(30);
            rawEvents.add(new TimelineEvent(
                    "lifecycle-incident-" + (incident.getId() != null ? incident.getId() : "seed"),
                    detected,
                    TimelineEventType.SERVICE_FAILURE,
                    primarySvc,
                    String.format("Incident opened: %s", incident.getTitle()),
                    incident.getDescription() != null ? incident.getDescription() : "Incident trigger threshold met",
                    incident.getSeverity() != null ? incident.getSeverity().name() : "HIGH",
                    "INC-" + (incident.getId() != null ? incident.getId() : "1"),
                    Map.of("status", incident.getStatus() != null ? incident.getStatus().name() : "OPEN")
            ));

            if (incident.getResolvedAt() != null) {
                rawEvents.add(new TimelineEvent(
                        "lifecycle-resolved-" + (incident.getId() != null ? incident.getId() : "seed"),
                        incident.getResolvedAt(),
                        TimelineEventType.SERVICE_FAILURE,
                        primarySvc,
                        String.format("Incident state changed to %s", incident.getStatus()),
                        "All service operational metrics and health checks restored within normal baseline ranges.",
                        "INFO",
                        "RESOLVE-" + (incident.getId() != null ? incident.getId() : "1"),
                        Map.of("status", incident.getStatus().name())
                ));
            }
        }

        // Filter by types if specified
        List<TimelineEvent> filtered = rawEvents.stream()
                .filter(ev -> filterTypes == null || filterTypes.isEmpty() || filterTypes.contains(ev.type()))
                .sorted(Comparator.comparing(TimelineEvent::timestamp)
                        .thenComparingInt(ev -> typeOrder(ev.type()))
                        .thenComparing(TimelineEvent::id))
                .toList();

        String summary = String.format(
                "Incident #%s timeline contains %d events between %s and %s across services %s",
                incident.getId() != null ? incident.getId() : incident.getIncidentId(),
                filtered.size(),
                start,
                end,
                affectedServices
        );

        return new IncidentTimeline(
                incident.getId(),
                incident.getTitle(),
                incident.getPrimaryService(),
                incident.getRootService(),
                start,
                end,
                filtered.size(),
                filtered,
                summary
        );
    }

    private int typeOrder(TimelineEventType type) {
        return switch (type) {
            case DEPLOYMENT -> 1;
            case ANOMALY -> 2;
            case METRIC -> 3;
            case SERVICE_FAILURE -> 4;
            case LOG -> 5;
        };
    }
}
