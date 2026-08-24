package com.aiincident.logprocessor.rca;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deterministic Mock LLM Provider for offline, deterministic, and fast AI RCA generation.
 * Parses the structured RcaContext JSON payload and produces a fully grounded, schema-valid RcaReport JSON.
 */
public class DeterministicMockLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(DeterministicMockLlmProvider.class);

    private final String modelName;
    private final ObjectMapper objectMapper;

    public DeterministicMockLlmProvider(String modelName) {
        this.modelName = modelName != null ? modelName : "mock-sre-engine";
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public String generateCompletion(String systemPrompt, String userPrompt) {
        try {
            // Extract and parse RcaContext from user prompt
            JsonNode contextNode = extractContextJson(userPrompt);
            RcaReport report = analyzeContext(contextNode);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        } catch (Exception e) {
            log.error("Error generating mock LLM RCA completion: {}", e.getMessage(), e);
            return generateFallbackReportJson(e.getMessage());
        }
    }

    private JsonNode extractContextJson(String userPrompt) throws Exception {
        if (userPrompt == null || userPrompt.isBlank()) {
            return objectMapper.createObjectNode();
        }

        // If userPrompt is directly JSON
        String trimmed = userPrompt.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return objectMapper.readTree(trimmed);
        }

        // If wrapped in markdown code blocks e.g. ```json ... ```
        int jsonStart = userPrompt.indexOf("```json");
        if (jsonStart != -1) {
            int start = userPrompt.indexOf("{", jsonStart);
            int end = userPrompt.lastIndexOf("}");
            if (start != -1 && end > start) {
                return objectMapper.readTree(userPrompt.substring(start, end + 1));
            }
        }

        int firstBrace = userPrompt.indexOf("{");
        int lastBrace = userPrompt.lastIndexOf("}");
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return objectMapper.readTree(userPrompt.substring(firstBrace, lastBrace + 1));
        }

        return objectMapper.createObjectNode();
    }

    private RcaReport analyzeContext(JsonNode root) {
        JsonNode summaryNode = root.path("summary");
        JsonNode primaryFailureNode = root.path("primaryFailure");
        JsonNode relevantLogsNode = root.path("relevantLogs");
        JsonNode timelineNode = root.path("timeline");
        JsonNode metricsNode = root.path("metrics");
        JsonNode dependenciesNode = root.path("dependencies");
        JsonNode similarIncidentsNode = root.path("similarHistoricalIncidents");
        JsonNode runbooksNode = root.path("relevantRunbooks");

        String incidentIdStr = summaryNode.path("incidentId").asText(summaryNode.path("id").asText("1"));
        String title = summaryNode.path("title").asText("System Incident");
        String primaryService = summaryNode.path("primaryService").asText("unknown-service");
        String resolvedRootService = summaryNode.path("rootService").asText(primaryService);

        // Extract primary candidate from deterministic analyzer if present
        JsonNode primaryCandNode = primaryFailureNode.path("primaryCandidate");
        if (!primaryCandNode.isMissingNode() && primaryCandNode.has("service")) {
            resolvedRootService = primaryCandNode.path("service").asText(resolvedRootService);
        }
        final String rootService = resolvedRootService;

        Set<String> affectedServicesSet = new HashSet<>();
        if (summaryNode.has("affectedServices") && summaryNode.path("affectedServices").isArray()) {
            for (JsonNode svc : summaryNode.path("affectedServices")) {
                affectedServicesSet.add(svc.asText());
            }
        }
        affectedServicesSet.add(primaryService);
        affectedServicesSet.add(rootService);

        // Collect logs and failure patterns
        List<RcaReport.EvidenceItem> evidenceItems = new ArrayList<>();
        boolean hasDirectFailureLogs = false;
        String detectedCategory = "UNKNOWN";
        String mainErrorMessage = "";

        if (relevantLogsNode.isArray()) {
            for (JsonNode logNode : relevantLogsNode) {
                String svc = logNode.path("service").asText("");
                String level = logNode.path("level").asText("");
                String eventType = logNode.path("eventType").asText("");
                String message = logNode.path("message").asText("");
                String eventId = logNode.path("eventId").asText("");
                String tsStr = logNode.path("timestamp").asText();
                Instant ts = parseInstant(tsStr);

                boolean isError = "ERROR".equalsIgnoreCase(level) || "CRITICAL".equalsIgnoreCase(level) || "FATAL".equalsIgnoreCase(level);
                boolean isRootSvc = svc.equalsIgnoreCase(rootService) || svc.equalsIgnoreCase(primaryService);

                if (isError) {
                    if (isRootSvc) {
                        hasDirectFailureLogs = true;
                    }
                    if (mainErrorMessage.isBlank()) {
                        mainErrorMessage = message;
                    }
                }

                if (eventType.contains("DB_") || eventType.contains("TIMEOUT") || message.toLowerCase().contains("pool") || message.toLowerCase().contains("connection")) {
                    detectedCategory = "DATABASE_TIMEOUT_OR_EXHAUSTION";
                } else if (eventType.contains("DEPLOYMENT") || message.toLowerCase().contains("deploy")) {
                    detectedCategory = "DEPLOYMENT_REGRESSION";
                } else if (eventType.contains("LATENCY") || message.toLowerCase().contains("latency")) {
                    detectedCategory = "HIGH_LATENCY_DEGRADATION";
                } else if (eventType.contains("UNAVAILABLE") || message.toLowerCase().contains("503")) {
                    if (detectedCategory.equals("UNKNOWN")) {
                        detectedCategory = "SERVICE_UNAVAILABLE";
                    }
                }

                evidenceItems.add(new RcaReport.EvidenceItem(
                        "LOG",
                        svc,
                        String.format("[%s] %s: %s", level, eventType, message),
                        eventId,
                        true,
                        ts
                ));
            }
        }

        // Add timeline anomalies and deployment evidence
        if (timelineNode.has("events") && timelineNode.path("events").isArray()) {
            for (JsonNode eventNode : timelineNode.path("events")) {
                String type = eventNode.path("type").asText("");
                String svc = eventNode.path("service").asText("");
                String titleText = eventNode.path("title").asText("");
                String descText = eventNode.path("description").asText("");
                String eventId = eventNode.path("eventId").asText(eventNode.path("id").asText(""));
                Instant ts = parseInstant(eventNode.path("timestamp").asText());

                if ("ANOMALY".equalsIgnoreCase(type) || "DEPLOYMENT".equalsIgnoreCase(type) || "METRIC".equalsIgnoreCase(type)) {
                    evidenceItems.add(new RcaReport.EvidenceItem(
                            type,
                            svc,
                            String.format("%s: %s", titleText, descText),
                            eventId,
                            true,
                            ts
                    ));
                }
            }
        }

        // Determine Confidence & Distinction between Observation vs Inference
        double confidenceScore;
        String confidenceLevel;
        String confidenceRationale;
        List<String> uncertaintyNotes = new ArrayList<>();

        if (hasDirectFailureLogs && !mainErrorMessage.isBlank()) {
            confidenceLevel = "HIGH";
            confidenceScore = 0.95;
            confidenceRationale = String.format(
                    "Direct failure evidence observed on root service '%s' with explicit error logs (%s). Primary failure analyzer scores temporal precedence and dependency hierarchy with high confidence.",
                    rootService, !mainErrorMessage.isBlank() ? mainErrorMessage : "observed errors"
            );
        } else if (!evidenceItems.isEmpty()) {
            confidenceLevel = "MEDIUM";
            confidenceScore = 0.65;
            confidenceRationale = String.format(
                    "Root cause on service '%s' inferred from downstream symptoms and metric spikes; however, direct root-level stack traces or failure logs were partially missing.",
                    rootService
            );
            uncertaintyNotes.add(String.format("Direct stack traces or debug logs for '%s' were not present in the current log window.", rootService));
        } else {
            confidenceLevel = "LOW";
            confidenceScore = 0.35;
            confidenceRationale = "Sparse evidence package. Analysis is based primarily on topological inference and high-level anomaly timestamps.";
            uncertaintyNotes.add("Telemetry data is sparse; additional log ingestion and metric resolution required for definitive certainty.");
        }

        // Formulate Root Cause statement & inference details
        String rootCauseStatement;
        String inferenceDetails;

        if ("DATABASE_TIMEOUT_OR_EXHAUSTION".equals(detectedCategory) || mainErrorMessage.toLowerCase().contains("db") || mainErrorMessage.toLowerCase().contains("pool") || mainErrorMessage.toLowerCase().contains("timeout")) {
            rootCauseStatement = String.format(
                    "Database connection pool exhaustion and query timeouts on '%s' caused service degradation and cascading failures to upstream callers.",
                    rootService
            );
            inferenceDetails = String.format(
                    "Observed Evidence: Service '%s' recorded database timeout events and connection pool saturation. " +
                    "Inference: Upstream services (e.g. %s) experienced HTTP 503 / downstream timeout errors as a direct cascading symptom of the '%s' database bottleneck.",
                    rootService, String.join(", ", affectedServicesSet), rootService
            );
        } else if ("DEPLOYMENT_REGRESSION".equals(detectedCategory)) {
            rootCauseStatement = String.format(
                    "Recent deployment on '%s' introduced a regression leading to error spikes and latency degradation.",
                    rootService
            );
            inferenceDetails = String.format(
                    "Observed Evidence: Deployment milestone correlated within 2 minutes prior to the first anomaly event on '%s'. " +
                    "Inference: Code or configuration changes in the new version triggered the observed error rates.",
                    rootService
            );
        } else {
            rootCauseStatement = String.format(
                    "Primary failure originated on service '%s', causing operational anomalies and cascading degradation across %s.",
                    rootService, String.join(", ", affectedServicesSet)
            );
            inferenceDetails = String.format(
                    "Observed Evidence: Initial anomaly and failure logs occurred first on '%s'. " +
                    "Inference: Subsequent errors on upstream services are downstream symptoms resulting from dependency graph relationships.",
                    rootService
            );
        }

        RcaReport.RootCause rootCause = new RcaReport.RootCause(
                rootCauseStatement,
                detectedCategory,
                rootService,
                inferenceDetails,
                hasDirectFailureLogs
        );

        // Alternative Hypotheses
        List<RcaReport.AlternativeHypothesis> alternativeHypotheses = new ArrayList<>();
        List<String> symptomList = affectedServicesSet.stream().filter(s -> !s.equalsIgnoreCase(rootService)).toList();

        if (!symptomList.isEmpty()) {
            alternativeHypotheses.add(new RcaReport.AlternativeHypothesis(
                    String.format("Network partition or transient network latency between %s and %s", String.join(", ", symptomList), rootService),
                    "LOW",
                    String.format("Service '%s' directly logged internal execution/database errors locally, confirming the fault is internal to '%s' rather than a network transmission drop.", rootService, rootService),
                    "Network packet capture or TCP retransmission metrics between service instances."
            ));
        }

        alternativeHypotheses.add(new RcaReport.AlternativeHypothesis(
                String.format("Traffic surge or DDoS overloading upstream ingress before propagating to %s", rootService),
                "LOW",
                "Total request volume across the operational metrics window remained stable and within baseline limits prior to the error spike.",
                "Ingress gateway request rate metrics and CDN traffic graphs."
        ));

        // Affected Services breakdown
        Map<String, String> serviceImpacts = new HashMap<>();
        serviceImpacts.put(rootService, "Root cause service: experiencing primary database/internal failure and error bursts.");
        for (String symptomSvc : symptomList) {
            serviceImpacts.put(symptomSvc, "Downstream symptom: receiving 503 / timeouts when calling " + rootService);
        }

        RcaReport.AffectedServices affectedServices = new RcaReport.AffectedServices(
                rootService,
                symptomList,
                serviceImpacts
        );

        // Recommended Investigation (Grounded strictly in evidence and runbooks)
        List<RcaReport.RecommendedInvestigation> recommendations = new ArrayList<>();
        String primaryRunbookId = "RB-001";
        if (runbooksNode.isArray() && runbooksNode.size() > 0) {
            primaryRunbookId = runbooksNode.get(0).path("documentId").asText("RB-001");
        }

        if ("DATABASE_TIMEOUT_OR_EXHAUSTION".equals(detectedCategory)) {
            recommendations.add(new RcaReport.RecommendedInvestigation(
                    String.format("Inspect active database connection pool metrics (HikariCP active vs max connections) on '%s'.", rootService),
                    "IMMEDIATE",
                    "Direct evidence of connection pool exhaustion and query timeouts.",
                    primaryRunbookId
            ));
            recommendations.add(new RcaReport.RecommendedInvestigation(
                    String.format("Check for slow or unindexed database queries blocking connection threads on '%s'.", rootService),
                    "HIGH",
                    "Database timeout errors observed during incident window.",
                    primaryRunbookId
            ));
            recommendations.add(new RcaReport.RecommendedInvestigation(
                    "Review database server CPU, memory, and max_connections settings in PostgreSQL.",
                    "MEDIUM",
                    "Ensure database infrastructure is sized appropriately for peak concurrency.",
                    primaryRunbookId
            ));
        } else if ("DEPLOYMENT_REGRESSION".equals(detectedCategory)) {
            recommendations.add(new RcaReport.RecommendedInvestigation(
                    String.format("Roll back recent deployment on '%s' to the previous stable release version.", rootService),
                    "IMMEDIATE",
                    "Incident onset closely correlated with deployment completion event.",
                    primaryRunbookId
            ));
            recommendations.add(new RcaReport.RecommendedInvestigation(
                    "Review recent commit diffs for breaking schema changes or missing connection timeouts.",
                    "HIGH",
                    "Deployment regression verification.",
                    primaryRunbookId
            ));
        } else {
            recommendations.add(new RcaReport.RecommendedInvestigation(
                    String.format("Inspect application logs and error rates for '%s'.", rootService),
                    "IMMEDIATE",
                    "Service identified as primary failure candidate.",
                    primaryRunbookId
            ));
            recommendations.add(new RcaReport.RecommendedInvestigation(
                    "Verify downstream dependencies and resource utilization on " + rootService,
                    "HIGH",
                    "Prevent cascading degradation across upstream services.",
                    primaryRunbookId
            ));
        }

        // Historical References
        List<RcaReport.HistoricalReference> historicalRefs = new ArrayList<>();
        if (similarIncidentsNode.isArray()) {
            for (JsonNode incNode : similarIncidentsNode) {
                String docId = incNode.path("documentId").asText("");
                String content = incNode.path("content").asText("");
                double score = incNode.path("similarityScore").asDouble(0.85);

                String refTitle = extractTitle(content, docId);
                historicalRefs.add(new RcaReport.HistoricalReference(
                        docId,
                        refTitle,
                        String.format("Vector similarity score %.2f. Similar failure pattern and symptom profile.", score),
                        "Remediated by increasing pool size and tuning slow query timeouts."
                ));
            }
        }

        if (runbooksNode.isArray()) {
            for (JsonNode rbNode : runbooksNode) {
                String docId = rbNode.path("documentId").asText("");
                String content = rbNode.path("content").asText("");
                String refTitle = extractTitle(content, docId);
                historicalRefs.add(new RcaReport.HistoricalReference(
                        docId,
                        refTitle,
                        "Matched mitigation runbook for operational failure resolution.",
                        "Step-by-step mitigation and rollback procedures."
                ));
            }
        }

        // Metadata
        RcaReport.RcaReportMetadata metadata = new RcaReport.RcaReportMetadata(
                Instant.now(),
                "mock",
                modelName,
                250,
                incidentIdStr
        );

        return new RcaReport(
                rootCause,
                new RcaReport.Confidence(confidenceLevel, confidenceScore, confidenceRationale),
                evidenceItems,
                alternativeHypotheses,
                affectedServices,
                recommendations,
                historicalRefs,
                uncertaintyNotes,
                metadata
        );
    }

    private String extractTitle(String content, String fallback) {
        if (content == null || content.isBlank()) {
            return fallback;
        }
        int titleIdx = content.indexOf("Title:");
        if (titleIdx != -1) {
            int endLine = content.indexOf("\n", titleIdx);
            if (endLine != -1) {
                return content.substring(titleIdx + 6, endLine).trim();
            }
            return content.substring(titleIdx + 6).trim();
        }
        int firstLineEnd = content.indexOf("\n");
        if (firstLineEnd != -1) {
            return content.substring(0, firstLineEnd).trim();
        }
        return content.length() > 60 ? content.substring(0, 60) + "..." : content;
    }

    private Instant parseInstant(String str) {
        if (str == null || str.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(str);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private String generateFallbackReportJson(String error) {
        return String.format("""
                {
                  "rootCause": {
                    "statement": "Analysis failed or data unavailable",
                    "category": "UNKNOWN",
                    "rootService": "unknown",
                    "inferenceDetails": "Error during analysis: %s",
                    "isDirectlyObserved": false
                  },
                  "confidence": {
                    "level": "LOW",
                    "score": 0.1,
                    "rationale": "Fallback report generated due to analysis error."
                  },
                  "evidence": [],
                  "alternativeHypotheses": [],
                  "affectedServices": {
                    "rootService": "unknown",
                    "symptomServices": [],
                    "serviceImpacts": {}
                  },
                  "recommendedInvestigation": [],
                  "historicalReferences": [],
                  "uncertaintyNotes": ["Analysis failed to complete cleanly: %s"],
                  "metadata": {
                    "analyzedAt": "%s",
                    "provider": "mock",
                    "model": "%s",
                    "executionLatencyMs": 0,
                    "incidentIdentifier": "unknown"
                  }
                }
                """, escapeJson(error), escapeJson(error), Instant.now().toString(), modelName);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", " ");
    }
}
