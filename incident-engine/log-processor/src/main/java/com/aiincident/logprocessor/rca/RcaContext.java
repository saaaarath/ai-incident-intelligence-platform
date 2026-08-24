package com.aiincident.logprocessor.rca;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.dependency.ServiceDependency;
import com.aiincident.logprocessor.dependency.ServiceDependencyService.ServiceTopology;
import com.aiincident.logprocessor.historical.embedding.SemanticSearchResult;
import com.aiincident.logprocessor.incident.IncidentStatus;
import com.aiincident.logprocessor.timeline.IncidentTimeline;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete structured Root Cause Analysis (RCA) context object for an incident.
 * Contains all collected operational evidence necessary for AI RCA without calling an LLM.
 */
public record RcaContext(
        IncidentSummary summary,
        IncidentTimeline timeline,
        List<RelevantLogEntry> relevantLogs,
        List<ServiceMetricsSummary> metrics,
        DependencyContext dependencies,
        PrimaryFailureAnalysis primaryFailure,
        List<SemanticSearchResult> similarHistoricalIncidents,
        List<SemanticSearchResult> relevantRunbooks,
        RcaContextMetadata metadata
) {

    /**
     * High-level summary of the incident.
     */
    public record IncidentSummary(
            Long id,
            String incidentId,
            String title,
            AnomalySeverity severity,
            IncidentStatus status,
            String primaryService,
            String rootService,
            Set<String> affectedServices,
            Instant startedAt,
            Instant detectedAt,
            Instant resolvedAt,
            String description,
            String metric,
            String fingerprint,
            String synthesizedSummary
    ) {}

    /**
     * Filtered, curated operational log entry relevant to the incident.
     */
    public record RelevantLogEntry(
            String eventId,
            Instant timestamp,
            String service,
            String level,
            String eventType,
            String traceId,
            String message,
            Map<String, Object> attributes
    ) {}

    /**
     * Aggregated metrics summary for a service during the incident window.
     */
    public record ServiceMetricsSummary(
            String service,
            Instant windowStart,
            Instant windowEnd,
            long totalRequests,
            long errorCount,
            double errorRate,
            double avgLatencyMs,
            double p50LatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            double maxLatencyMs
    ) {}

    /**
     * Service dependency context for the affected services.
     */
    public record DependencyContext(
            List<ServiceTopology> serviceTopologies,
            List<ServiceDependency> directDependencies,
            Set<String> upstreamCallers,
            Set<String> downstreamDependencies
    ) {}

    /**
     * Metadata regarding RCA context construction.
     */
    public record RcaContextMetadata(
            Instant generatedAt,
            Instant windowStart,
            Instant windowEnd,
            int bufferMinutesUsed,
            int totalLogsConsidered,
            int relevantLogsIncluded,
            int timelineEventsCount,
            int similarIncidentsCount,
            int relevantRunbooksCount
    ) {}
}
