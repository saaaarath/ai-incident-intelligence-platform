package com.aiincident.logprocessor.rca;

import com.aiincident.logprocessor.historical.embedding.SemanticSearchResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates AI-generated Root Cause Analysis (RCA) reports for schema compliance,
 * confidence range calibration, and strict evidence grounding against the incident context.
 * 
 * Prevents unsupported or hallucinated RCA claims from being accepted.
 */
@Component
public class RcaEvidenceGroundingValidator {

    private static final Logger log = LoggerFactory.getLogger(RcaEvidenceGroundingValidator.class);

    private static final Set<String> VALID_CONFIDENCE_LEVELS = Set.of("HIGH", "MEDIUM", "LOW");

    /**
     * Validate an RCA report against the originating RcaContext evidence package.
     */
    public RcaValidationResult validate(RcaReport report, RcaContext context) {
        if (report == null) {
            return RcaValidationResult.invalid(
                    RcaValidationResult.RcaValidationStatus.INVALID_SCHEMA,
                    List.of("RCA report is null"),
                    List.of(),
                    List.of()
            );
        }

        List<String> errors = new ArrayList<>();
        List<String> groundingViolations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Validate Schema & Required Fields
        validateRequiredFields(report, errors);

        // 2. Validate Confidence Range & Grounding Consistency
        validateConfidence(report, context, errors, warnings);

        // 3. Validate Evidence Grounding against context
        if (context != null) {
            validateEvidenceGrounding(report, context, groundingViolations, warnings);
        }

        // 4. Synthesize final validation result
        if (!errors.isEmpty()) {
            log.warn("RCA report failed schema validation: {}", errors);
            return RcaValidationResult.invalid(
                    RcaValidationResult.RcaValidationStatus.INVALID_SCHEMA,
                    errors,
                    groundingViolations,
                    warnings
            );
        }

        if (!groundingViolations.isEmpty()) {
            log.warn("RCA report failed evidence grounding: {}", groundingViolations);
            return RcaValidationResult.invalid(
                    RcaValidationResult.RcaValidationStatus.UNGROUNDED,
                    errors,
                    groundingViolations,
                    warnings
            );
        }

        return RcaValidationResult.validWithWarnings(warnings);
    }

    private void validateRequiredFields(RcaReport report, List<String> errors) {
        // Root Cause
        if (report.rootCause() == null) {
            errors.add("Required field 'rootCause' is missing");
        } else {
            if (report.rootCause().statement() == null || report.rootCause().statement().isBlank()) {
                errors.add("'rootCause.statement' must not be empty");
            }
            if (report.rootCause().category() == null || report.rootCause().category().isBlank()) {
                errors.add("'rootCause.category' must not be empty");
            }
            if (report.rootCause().rootService() == null || report.rootCause().rootService().isBlank()) {
                errors.add("'rootCause.rootService' must not be empty");
            }
        }

        // Confidence
        if (report.confidence() == null) {
            errors.add("Required field 'confidence' is missing");
        } else {
            if (report.confidence().level() == null || !VALID_CONFIDENCE_LEVELS.contains(report.confidence().level().toUpperCase(Locale.ROOT))) {
                errors.add("'confidence.level' must be one of: HIGH, MEDIUM, LOW");
            }
            if (Double.isNaN(report.confidence().score()) || report.confidence().score() < 0.0 || report.confidence().score() > 1.0) {
                errors.add("'confidence.score' must be between 0.0 and 1.0 (found: " + report.confidence().score() + ")");
            }
            if (report.confidence().rationale() == null || report.confidence().rationale().isBlank()) {
                errors.add("'confidence.rationale' must not be empty");
            }
        }

        // Affected Services
        if (report.affectedServices() == null) {
            errors.add("Required field 'affectedServices' is missing");
        } else {
            if (report.affectedServices().rootService() == null || report.affectedServices().rootService().isBlank()) {
                errors.add("'affectedServices.rootService' must not be empty");
            }
            if (report.affectedServices().symptomServices() == null) {
                errors.add("'affectedServices.symptomServices' list must not be null");
            }
        }
    }

    private void validateConfidence(RcaReport report, RcaContext context, List<String> errors, List<String> warnings) {
        if (report.confidence() == null) {
            return;
        }

        String level = report.confidence().level() != null ? report.confidence().level().toUpperCase(Locale.ROOT) : "";
        double score = report.confidence().score();

        // Check if HIGH confidence is claimed on sparse or ungrounded data
        if ("HIGH".equals(level) || score >= 0.8) {
            boolean hasDirectObservation = report.evidence() != null && report.evidence().stream().anyMatch(RcaReport.EvidenceItem::isDirectObservation);
            boolean contextHasLogs = context != null && context.relevantLogs() != null && !context.relevantLogs().isEmpty();

            if (!hasDirectObservation && !contextHasLogs) {
                warnings.add("High confidence claimed without direct log evidence in context; calibrated confidence recommended");
            }
        }
    }

    private void validateEvidenceGrounding(
            RcaReport report,
            RcaContext context,
            List<String> groundingViolations,
            List<String> warnings
    ) {
        // Collect known valid context entities
        Set<String> validServices = extractValidServices(context);
        Set<String> validLogEventIds = extractValidLogEventIds(context);
        Set<String> validHistoricalDocIds = extractValidHistoricalDocIds(context);
        Set<String> validRunbookDocIds = extractValidRunbookDocIds(context);

        // 1. Validate Evidence Items
        if (report.evidence() != null) {
            for (RcaReport.EvidenceItem item : report.evidence()) {
                if (item.service() != null && !item.service().isBlank() && !validServices.isEmpty()) {
                    if (!isKnownService(item.service(), validServices)) {
                        groundingViolations.add(String.format("Evidence item cites unknown service '%s' not present in incident context", item.service()));
                    }
                }

                // If sourceId references an event ID (e.g. ev-...)
                if (item.sourceId() != null && item.sourceId().startsWith("ev-") && !validLogEventIds.isEmpty()) {
                    if (!validLogEventIds.contains(item.sourceId().trim())) {
                        groundingViolations.add(String.format("Evidence item cites fabricated log eventId '%s' not in incident context", item.sourceId()));
                    }
                }
            }
        }

        // 2. Validate Affected Services
        if (report.affectedServices() != null) {
            String rootService = report.affectedServices().rootService();
            if (rootService != null && !validServices.isEmpty() && !isKnownService(rootService, validServices)) {
                groundingViolations.add(String.format("Affected root service '%s' does not exist in incident context", rootService));
            }

            if (report.affectedServices().symptomServices() != null) {
                for (String symptomService : report.affectedServices().symptomServices()) {
                    if (symptomService != null && !validServices.isEmpty() && !isKnownService(symptomService, validServices)) {
                        groundingViolations.add(String.format("Affected symptom service '%s' does not exist in incident context", symptomService));
                    }
                }
            }
        }

        // 3. Validate Historical References
        if (report.historicalReferences() != null) {
            for (RcaReport.HistoricalReference ref : report.historicalReferences()) {
                if (ref.referenceId() != null && !ref.referenceId().isBlank()) {
                    String cleanId = ref.referenceId().trim();
                    boolean exists = isDocumentIdMatched(cleanId, validHistoricalDocIds) || isDocumentIdMatched(cleanId, validRunbookDocIds);
                    if (!exists && (!validHistoricalDocIds.isEmpty() || !validRunbookDocIds.isEmpty())) {
                        groundingViolations.add(String.format("Historical reference ID '%s' was not retrieved in incident knowledge context", cleanId));
                    }
                }
            }
        }

        // 4. Validate Recommended Investigation Runbook References
        if (report.recommendedInvestigation() != null) {
            for (RcaReport.RecommendedInvestigation rec : report.recommendedInvestigation()) {
                if (rec.runbookRef() != null && !rec.runbookRef().isBlank() && !"NONE".equalsIgnoreCase(rec.runbookRef())) {
                    String cleanRunbook = rec.runbookRef().trim();
                    boolean exists = isDocumentIdMatched(cleanRunbook, validRunbookDocIds) || isDocumentIdMatched(cleanRunbook, validHistoricalDocIds);
                    if (!exists && (!validRunbookDocIds.isEmpty() || !validHistoricalDocIds.isEmpty())) {
                        groundingViolations.add(String.format("Investigation recommendation cites ungrounded runbookRef '%s'", cleanRunbook));
                    }
                }
            }
        }
    }

    private boolean isKnownService(String service, Set<String> validServices) {
        String lower = service.toLowerCase(Locale.ROOT).trim();
        for (String valid : validServices) {
            if (lower.equals(valid.toLowerCase(Locale.ROOT).trim()) || lower.contains(valid.toLowerCase(Locale.ROOT).trim()) || valid.toLowerCase(Locale.ROOT).contains(lower)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDocumentIdMatched(String id, Set<String> validDocIds) {
        String lower = id.toLowerCase(Locale.ROOT).trim();
        for (String valid : validDocIds) {
            String validLower = valid.toLowerCase(Locale.ROOT).trim();
            if (lower.equals(validLower) || lower.contains(validLower) || validLower.contains(lower)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> extractValidServices(RcaContext context) {
        Set<String> services = new HashSet<>();
        if (context.summary() != null) {
            if (context.summary().primaryService() != null) services.add(context.summary().primaryService());
            if (context.summary().rootService() != null) services.add(context.summary().rootService());
            if (context.summary().affectedServices() != null) services.addAll(context.summary().affectedServices());
        }
        if (context.relevantLogs() != null) {
            for (RcaContext.RelevantLogEntry logEntry : context.relevantLogs()) {
                if (logEntry.service() != null) services.add(logEntry.service());
            }
        }
        if (context.metrics() != null) {
            for (RcaContext.ServiceMetricsSummary metric : context.metrics()) {
                if (metric.service() != null) services.add(metric.service());
            }
        }
        if (context.dependencies() != null) {
            if (context.dependencies().upstreamCallers() != null) services.addAll(context.dependencies().upstreamCallers());
            if (context.dependencies().downstreamDependencies() != null) services.addAll(context.dependencies().downstreamDependencies());
        }
        return services;
    }

    private Set<String> extractValidLogEventIds(RcaContext context) {
        Set<String> eventIds = new HashSet<>();
        if (context.relevantLogs() != null) {
            for (RcaContext.RelevantLogEntry logEntry : context.relevantLogs()) {
                if (logEntry.eventId() != null && !logEntry.eventId().isBlank()) {
                    eventIds.add(logEntry.eventId().trim());
                }
            }
        }
        if (context.timeline() != null && context.timeline().events() != null) {
            for (var event : context.timeline().events()) {
                if (event.id() != null && !event.id().isBlank()) {
                    eventIds.add(event.id().trim());
                }
                if (event.sourceEventId() != null && !event.sourceEventId().isBlank()) {
                    eventIds.add(event.sourceEventId().trim());
                }
            }
        }
        return eventIds;
    }

    private Set<String> extractValidHistoricalDocIds(RcaContext context) {
        Set<String> docIds = new HashSet<>();
        if (context.similarHistoricalIncidents() != null) {
            for (SemanticSearchResult doc : context.similarHistoricalIncidents()) {
                if (doc.getDocumentId() != null) {
                    docIds.add(doc.getDocumentId().trim());
                }
            }
        }
        return docIds;
    }

    private Set<String> extractValidRunbookDocIds(RcaContext context) {
        Set<String> docIds = new HashSet<>();
        if (context.relevantRunbooks() != null) {
            for (SemanticSearchResult doc : context.relevantRunbooks()) {
                if (doc.getDocumentId() != null) {
                    docIds.add(doc.getDocumentId().trim());
                }
            }
        }
        return docIds;
    }
}
