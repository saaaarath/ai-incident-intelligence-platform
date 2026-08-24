package com.aiincident.logprocessor.rca;

import com.aiincident.logprocessor.dependency.ServiceDependency;
import com.aiincident.logprocessor.dependency.ServiceDependencyService;
import com.aiincident.logprocessor.dependency.ServiceDependencyService.ServiceTopology;
import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.historical.embedding.IncidentRetrievalService;
import com.aiincident.logprocessor.historical.embedding.SemanticSearchResult;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentEvidence;
import com.aiincident.logprocessor.incident.IncidentEvidenceRepository;
import com.aiincident.logprocessor.incident.IncidentRepository;
import com.aiincident.logprocessor.metrics.MetricsAggregationService;
import com.aiincident.logprocessor.metrics.OperationalMetrics;
import com.aiincident.logprocessor.repository.LogEventRepository;
import com.aiincident.logprocessor.timeline.IncidentTimeline;
import com.aiincident.logprocessor.timeline.IncidentTimelineService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for assembling the complete Root Cause Analysis (RCA) context package.
 * Aggregates summary, timeline, filtered logs, operational metrics, dependency relationships,
 * primary failure candidates, similar historical incidents, and relevant runbooks for AI RCA.
 */
@Service
public class RcaContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(RcaContextBuilder.class);

    private final IncidentRepository incidentRepository;
    private final IncidentEvidenceRepository evidenceRepository;
    private final IncidentTimelineService timelineService;
    private final LogEventRepository logEventRepository;
    private final MetricsAggregationService metricsService;
    private final ServiceDependencyService dependencyService;
    private final PrimaryFailureAnalyzer primaryFailureAnalyzer;
    private final IncidentRetrievalService incidentRetrievalService;
    private final ObjectMapper objectMapper;

    @Autowired
    public RcaContextBuilder(
            IncidentRepository incidentRepository,
            @Autowired(required = false) IncidentEvidenceRepository evidenceRepository,
            @Autowired(required = false) IncidentTimelineService timelineService,
            @Autowired(required = false) LogEventRepository logEventRepository,
            @Autowired(required = false) MetricsAggregationService metricsService,
            @Autowired(required = false) ServiceDependencyService dependencyService,
            @Autowired(required = false) PrimaryFailureAnalyzer primaryFailureAnalyzer,
            @Autowired(required = false) IncidentRetrievalService incidentRetrievalService,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.evidenceRepository = evidenceRepository;
        this.timelineService = timelineService;
        this.logEventRepository = logEventRepository;
        this.metricsService = metricsService;
        this.dependencyService = dependencyService;
        this.primaryFailureAnalyzer = primaryFailureAnalyzer;
        this.incidentRetrievalService = incidentRetrievalService;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    /**
     * Build RCA Context for an incident identified by numeric ID or UUID string with default options.
     */
    @Transactional(readOnly = true)
    public Optional<RcaContext> buildContext(String incidentIdentifier) {
        return buildContext(incidentIdentifier, RcaContextOptions.defaults());
    }

    /**
     * Build RCA Context for an incident identified by numeric ID or UUID string with custom options.
     */
    @Transactional(readOnly = true)
    public Optional<RcaContext> buildContext(String incidentIdentifier, RcaContextOptions options) {
        Optional<Incident> incidentOpt = resolveIncident(incidentIdentifier);
        if (incidentOpt.isEmpty()) {
            return Optional.empty();
        }
        RcaContextOptions opt = (options != null) ? options : RcaContextOptions.defaults();
        return Optional.of(buildContextForIncident(incidentOpt.get(), opt));
    }

    /**
     * Build RCA Context for an incident identified by database ID.
     */
    @Transactional(readOnly = true)
    public Optional<RcaContext> buildContext(Long incidentId, RcaContextOptions options) {
        if (incidentId == null) {
            return Optional.empty();
        }
        return incidentRepository.findById(incidentId)
                .map(inc -> buildContextForIncident(inc, options != null ? options : RcaContextOptions.defaults()));
    }

    /**
     * Build complete RCA Context object for a loaded Incident instance.
     */
    @Transactional(readOnly = true)
    public RcaContext buildContextForIncident(Incident incident, RcaContextOptions options) {
        RcaContextOptions opt = (options != null) ? options : RcaContextOptions.defaults();
        int bufferMinutes = Math.max(0, opt.bufferMinutes());

        Instant start = (incident.getStartedAt() != null ? incident.getStartedAt() :
                (incident.getDetectedAt() != null ? incident.getDetectedAt() : Instant.now()))
                .minus(Duration.ofMinutes(bufferMinutes));
        Instant end = (incident.getResolvedAt() != null ? incident.getResolvedAt() :
                (incident.getLastEventAt() != null ? incident.getLastEventAt() : Instant.now()))
                .plus(Duration.ofMinutes(bufferMinutes));

        // 1. Resolve Affected Services & Evidence
        Set<String> affectedServices = new HashSet<>();
        if (incident.getAffectedServices() != null) {
            for (String s : incident.getAffectedServices()) {
                if (s != null && !s.isBlank()) affectedServices.add(s.trim().toLowerCase());
            }
        }
        if (incident.getPrimaryService() != null && !incident.getPrimaryService().isBlank()) {
            affectedServices.add(incident.getPrimaryService().trim().toLowerCase());
        }
        if (incident.getRootService() != null && !incident.getRootService().isBlank()) {
            affectedServices.add(incident.getRootService().trim().toLowerCase());
        }

        List<IncidentEvidence> evidenceList = incident.getEvidence();
        if ((evidenceList == null || evidenceList.isEmpty()) && evidenceRepository != null && incident.getId() != null) {
            evidenceList = evidenceRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
        }
        if (evidenceList == null) {
            evidenceList = List.of();
        }

        // Trace IDs correlated to the incident
        Set<String> correlatedTraceIds = new HashSet<>();
        for (IncidentEvidence ev : evidenceList) {
            if (ev.getTraceId() != null && !ev.getTraceId().isBlank()) {
                correlatedTraceIds.add(ev.getTraceId().trim());
            }
            if (ev.getService() != null && !ev.getService().isBlank()) {
                affectedServices.add(ev.getService().trim().toLowerCase());
            }
        }

        // 2. Incident Summary
        String synthesizedSummary = "";
        if (incidentRetrievalService != null) {
            synthesizedSummary = incidentRetrievalService.synthesizeIncidentSummary(incident, evidenceList);
        } else {
            synthesizedSummary = String.format("%s on service %s: %s",
                    incident.getTitle(), incident.getPrimaryService(), incident.getDescription());
        }

        RcaContext.IncidentSummary summary = new RcaContext.IncidentSummary(
                incident.getId(),
                incident.getIncidentId(),
                incident.getTitle(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getPrimaryService(),
                incident.getRootService(),
                new HashSet<>(affectedServices),
                incident.getStartedAt(),
                incident.getDetectedAt(),
                incident.getResolvedAt(),
                incident.getDescription(),
                incident.getMetric(),
                incident.getFingerprint(),
                synthesizedSummary
        );

        // 3. Incident Timeline
        IncidentTimeline timeline = null;
        if (timelineService != null) {
            timeline = timelineService.buildTimelineForIncident(incident, bufferMinutes, null);
        } else {
            timeline = new IncidentTimeline(
                    incident.getId(),
                    incident.getTitle(),
                    incident.getPrimaryService(),
                    incident.getRootService(),
                    start,
                    end,
                    0,
                    List.of(),
                    "Timeline unavailable"
            );
        }

        // 4. Relevant Logs (Filtered to affected services & time window, prioritized, bounded)
        int totalLogsConsidered = 0;
        List<RcaContext.RelevantLogEntry> relevantLogs = new ArrayList<>();
        if (logEventRepository != null) {
            List<ProcessedLogEvent> candidateLogs = logEventRepository.findByTimestampBetweenOrderByTimestampAsc(start, end);
            totalLogsConsidered = candidateLogs.size();

            // Filter out logs that do not belong to affected services or correlated traceIds
            List<ProcessedLogEvent> filteredLogs = candidateLogs.stream()
                    .filter(logEvent -> {
                        String svc = (logEvent.getService() != null) ? logEvent.getService().trim().toLowerCase() : "";
                        boolean matchesService = affectedServices.contains(svc);
                        boolean matchesTrace = logEvent.getTraceId() != null && correlatedTraceIds.contains(logEvent.getTraceId().trim());
                        return matchesService || matchesTrace;
                    })
                    .toList();

            // Prioritize logs: ERROR > WARN > trace-match > others, then chronologically
            List<ProcessedLogEvent> prioritized = new ArrayList<>(filteredLogs);
            prioritized.sort(Comparator.comparingInt((ProcessedLogEvent l) -> logPriority(l, correlatedTraceIds))
                    .thenComparing(ProcessedLogEvent::getTimestamp));

            // Cap to maxRelevantLogs
            int limit = Math.max(1, opt.maxRelevantLogs());
            List<ProcessedLogEvent> boundedLogs = prioritized.stream().limit(limit).toList();

            for (ProcessedLogEvent l : boundedLogs) {
                Map<String, Object> attrs = parseMetadata(l.getMetadata());
                relevantLogs.add(new RcaContext.RelevantLogEntry(
                        l.getEventId(),
                        l.getTimestamp(),
                        l.getService(),
                        l.getLevel(),
                        l.getEventType(),
                        l.getTraceId(),
                        l.getMessage(),
                        attrs
                ));
            }
        }

        // 5. Operational Metrics for Affected Services
        List<RcaContext.ServiceMetricsSummary> metricsSummaries = new ArrayList<>();
        if (metricsService != null) {
            for (String svc : affectedServices) {
                try {
                    OperationalMetrics om = metricsService.getSummary(svc, start, end);
                    if (om != null) {
                        double avgLat = (om.latency() != null && om.latency().avg() != null) ? om.latency().avg() : 0.0;
                        double p50Lat = (om.latency() != null && om.latency().p50() != null) ? om.latency().p50() : 0.0;
                        double p95Lat = (om.latency() != null && om.latency().p95() != null) ? om.latency().p95() : 0.0;
                        double p99Lat = (om.latency() != null && om.latency().p99() != null) ? om.latency().p99() : 0.0;
                        double maxLat = (om.latency() != null && om.latency().max() != null) ? om.latency().max() : 0.0;

                        metricsSummaries.add(new RcaContext.ServiceMetricsSummary(
                                svc,
                                start,
                                end,
                                om.totalEvents(),
                                om.errorCount(),
                                om.errorRate(),
                                avgLat,
                                p50Lat,
                                p95Lat,
                                p99Lat,
                                maxLat
                        ));
                    }
                } catch (Exception e) {
                    log.debug("Could not calculate metrics summary for service {}: {}", svc, e.getMessage());
                }
            }
        }

        // 6. Service Dependency Topology
        RcaContext.DependencyContext dependencyContext;
        if (dependencyService != null) {
            List<ServiceTopology> topologies = new ArrayList<>();
            Set<String> allUpstream = new HashSet<>();
            Set<String> allDownstream = new HashSet<>();
            List<ServiceDependency> directDependencies = new ArrayList<>();

            for (String svc : affectedServices) {
                ServiceTopology topo = dependencyService.getServiceTopology(svc);
                if (topo != null) {
                    topologies.add(topo);
                    if (topo.upstream() != null) allUpstream.addAll(topo.upstream());
                    if (topo.downstream() != null) allDownstream.addAll(topo.downstream());
                }
            }

            List<ServiceDependency> allDeps = dependencyService.getAllDependencies();
            for (ServiceDependency dep : allDeps) {
                String src = dep.getSourceService() != null ? dep.getSourceService().toLowerCase() : "";
                String tgt = dep.getTargetService() != null ? dep.getTargetService().toLowerCase() : "";
                if (affectedServices.contains(src) && affectedServices.contains(tgt)) {
                    directDependencies.add(dep);
                }
            }

            dependencyContext = new RcaContext.DependencyContext(
                    topologies,
                    directDependencies,
                    allUpstream,
                    allDownstream
            );
        } else {
            dependencyContext = new RcaContext.DependencyContext(List.of(), List.of(), Set.of(), Set.of());
        }

        // 7. Primary Failure vs Symptoms Analysis
        PrimaryFailureAnalysis primaryFailure = null;
        if (primaryFailureAnalyzer != null) {
            primaryFailure = primaryFailureAnalyzer.analyzeIncident(incident);
        }

        // 8. Semantically Retrieved Similar Historical Incidents & Runbooks
        List<SemanticSearchResult> similarIncidents = List.of();
        List<SemanticSearchResult> relevantRunbooks = List.of();
        if (incidentRetrievalService != null) {
            String identifier = incident.getIncidentId() != null ? incident.getIncidentId() : String.valueOf(incident.getId());
            try {
                similarIncidents = incidentRetrievalService.findSimilarIncidents(identifier, opt.historicalTopK());
            } catch (Exception e) {
                log.debug("Error retrieving similar incidents for RCA context: {}", e.getMessage());
            }
            try {
                relevantRunbooks = incidentRetrievalService.findRelevantRunbooks(identifier, opt.runbookTopK());
            } catch (Exception e) {
                log.debug("Error retrieving relevant runbooks for RCA context: {}", e.getMessage());
            }
        }

        // 9. Context Construction Metadata
        RcaContext.RcaContextMetadata metadata = new RcaContext.RcaContextMetadata(
                Instant.now(),
                start,
                end,
                bufferMinutes,
                totalLogsConsidered,
                relevantLogs.size(),
                timeline != null && timeline.events() != null ? timeline.events().size() : 0,
                similarIncidents.size(),
                relevantRunbooks.size()
        );

        return new RcaContext(
                summary,
                timeline,
                relevantLogs,
                metricsSummaries,
                dependencyContext,
                primaryFailure,
                similarIncidents,
                relevantRunbooks,
                metadata
        );
    }

    private int logPriority(ProcessedLogEvent logEvent, Set<String> traceIds) {
        String level = logEvent.getLevel() != null ? logEvent.getLevel().toUpperCase() : "";
        if ("ERROR".equals(level) || "FATAL".equals(level) || "CRITICAL".equals(level)) {
            return 1;
        }
        if ("WARN".equals(level) || "WARNING".equals(level)) {
            return 2;
        }
        if (logEvent.getTraceId() != null && traceIds.contains(logEvent.getTraceId().trim())) {
            return 3;
        }
        return 4;
    }

    private Map<String, Object> parseMetadata(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metaJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", metaJson);
        }
    }

    public Optional<Incident> resolveIncident(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        if (incidentRetrievalService != null) {
            return incidentRetrievalService.resolveIncident(identifier);
        }
        try {
            Long numericId = Long.parseLong(identifier.trim());
            Optional<Incident> byId = incidentRepository.findById(numericId);
            if (byId.isPresent()) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
        }
        return incidentRepository.findByIncidentId(identifier.trim());
    }

    /**
     * Configuration options for building RCA Context.
     */
    public record RcaContextOptions(
            int bufferMinutes,
            int maxRelevantLogs,
            int historicalTopK,
            int runbookTopK
    ) {
        public static RcaContextOptions defaults() {
            return new RcaContextOptions(5, 50, 3, 3);
        }
    }
}
