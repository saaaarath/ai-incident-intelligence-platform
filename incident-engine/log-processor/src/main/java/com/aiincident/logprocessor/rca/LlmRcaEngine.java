package com.aiincident.logprocessor.rca;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AI Root Cause Analysis (RCA) Engine.
 * Takes structured incident context, prompts the LLM with rigorous RCA instructions,
 * parses the structured RCA report, and strictly validates evidence grounding.
 */
@Service
public class LlmRcaEngine {

    private static final Logger log = LoggerFactory.getLogger(LlmRcaEngine.class);

    private final LlmProvider llmProvider;
    private final RcaContextBuilder contextBuilder;
    private final RcaPromptFormatter promptFormatter;
    private final RcaEvidenceGroundingValidator validator;
    private final ObjectMapper objectMapper;

    @Autowired
    public LlmRcaEngine(
            LlmProvider llmProvider,
            RcaContextBuilder contextBuilder,
            RcaPromptFormatter promptFormatter,
            @Autowired(required = false) RcaEvidenceGroundingValidator validator,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this.llmProvider = llmProvider;
        this.contextBuilder = contextBuilder;
        this.promptFormatter = promptFormatter;
        this.validator = (validator != null) ? validator : new RcaEvidenceGroundingValidator();
        this.objectMapper = (objectMapper != null)
                ? objectMapper.copy().registerModule(new JavaTimeModule()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                : new ObjectMapper().registerModule(new JavaTimeModule()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public LlmRcaEngine(
            LlmProvider llmProvider,
            RcaContextBuilder contextBuilder,
            RcaPromptFormatter promptFormatter,
            ObjectMapper objectMapper) {
        this(llmProvider, contextBuilder, promptFormatter, new RcaEvidenceGroundingValidator(), objectMapper);
    }

    /**
     * Analyze an incident identified by numeric ID or UUID string.
     */
    public Optional<RcaReport> analyzeIncident(String incidentIdentifier) {
        return analyzeIncident(incidentIdentifier, RcaContextBuilder.RcaContextOptions.defaults());
    }

    /**
     * Analyze an incident with custom context builder options.
     */
    public Optional<RcaReport> analyzeIncident(String incidentIdentifier, RcaContextBuilder.RcaContextOptions options) {
        Optional<RcaContext> contextOpt = contextBuilder.buildContext(incidentIdentifier, options);
        return contextOpt.map(this::analyzeContext);
    }

    /**
     * Analyze an incident identified by Long database ID.
     */
    public Optional<RcaReport> analyzeIncident(Long incidentId) {
        if (incidentId == null) {
            return Optional.empty();
        }
        return analyzeIncident(String.valueOf(incidentId));
    }

    /**
     * Validate an arbitrary RCA report against an RcaContext evidence package.
     */
    public RcaValidationResult validateReport(RcaReport report, RcaContext context) {
        return validator.validate(report, context);
    }

    /**
     * Generate an AI Root Cause Analysis report for a provided RcaContext and validate evidence grounding.
     */
    public RcaReport analyzeContext(RcaContext context) {
        if (context == null) {
            throw new IllegalArgumentException("RcaContext must not be null");
        }

        long startTime = System.currentTimeMillis();
        String systemPrompt = promptFormatter.formatSystemPrompt();
        String userPrompt = promptFormatter.formatUserPrompt(context);

        log.info("Executing AI RCA for incident #{} (service='{}') using provider='{}', model='{}'",
                context.summary() != null ? context.summary().incidentId() : "unknown",
                context.summary() != null ? context.summary().primaryService() : "unknown",
                llmProvider.getProviderName(),
                llmProvider.getModelName());

        String rawResponse = llmProvider.generateCompletion(systemPrompt, userPrompt);
        long latency = System.currentTimeMillis() - startTime;

        RcaReport unvalidatedReport = parseAndValidateReport(rawResponse, context, latency);

        // Perform Evidence Grounding & Schema Validation
        RcaValidationResult validation = validator.validate(unvalidatedReport, context);

        RcaReport finalReport;
        if (!validation.isValid() || !validation.isGrounded()) {
            log.warn("AI RCA output for incident #{} failed validation/grounding (status={}): errors={}, violations={}",
                    context.summary() != null ? context.summary().incidentId() : "unknown",
                    validation.status(),
                    validation.errors(),
                    validation.groundingViolations());

            List<String> combinedUncertainty = new ArrayList<>(unvalidatedReport.uncertaintyNotes() != null ? unvalidatedReport.uncertaintyNotes() : List.of());
            if (!validation.errors().isEmpty()) {
                combinedUncertainty.add("Schema validation errors: " + String.join("; ", validation.errors()));
            }
            if (!validation.groundingViolations().isEmpty()) {
                combinedUncertainty.add("Evidence grounding violations: " + String.join("; ", validation.groundingViolations()));
            }

            finalReport = new RcaReport(
                    unvalidatedReport.rootCause(),
                    unvalidatedReport.confidence(),
                    unvalidatedReport.evidence(),
                    unvalidatedReport.alternativeHypotheses(),
                    unvalidatedReport.affectedServices(),
                    unvalidatedReport.recommendedInvestigation(),
                    unvalidatedReport.historicalReferences(),
                    combinedUncertainty,
                    unvalidatedReport.metadata(),
                    validation
            );
        } else {
            finalReport = unvalidatedReport.withValidation(validation);
        }

        log.info("Completed AI RCA for incident #{} in {}ms [confidence={}, rootService='{}', validationStatus={}]",
                context.summary() != null ? context.summary().incidentId() : "unknown",
                latency,
                finalReport.confidence() != null ? finalReport.confidence().level() : "UNKNOWN",
                finalReport.rootCause() != null ? finalReport.rootCause().rootService() : "unknown",
                validation.status());

        return finalReport;
    }

    private RcaReport parseAndValidateReport(String rawResponse, RcaContext context, long latency) {
        String incidentIdStr = (context.summary() != null && context.summary().incidentId() != null)
                ? context.summary().incidentId()
                : (context.summary() != null && context.summary().id() != null ? String.valueOf(context.summary().id()) : "unknown");

        try {
            String jsonToParse = extractJsonPayload(rawResponse);
            RcaReport parsed = objectMapper.readValue(jsonToParse, RcaReport.class);

            // Enrich/validate metadata if missing
            RcaReport.RcaReportMetadata metadata = parsed.metadata();
            if (metadata == null) {
                metadata = new RcaReport.RcaReportMetadata(
                        Instant.now(),
                        llmProvider.getProviderName(),
                        llmProvider.getModelName(),
                        latency,
                        incidentIdStr
                );
            }

            return new RcaReport(
                    parsed.rootCause() != null ? parsed.rootCause() : new RcaReport.RootCause("Unknown root cause", "UNKNOWN", "unknown", "No inference available", false),
                    parsed.confidence() != null ? parsed.confidence() : new RcaReport.Confidence("LOW", 0.3, "Confidence not provided by model"),
                    parsed.evidence() != null ? parsed.evidence() : List.of(),
                    parsed.alternativeHypotheses() != null ? parsed.alternativeHypotheses() : List.of(),
                    parsed.affectedServices() != null ? parsed.affectedServices() : new RcaReport.AffectedServices("unknown", List.of(), Map.of()),
                    parsed.recommendedInvestigation() != null ? parsed.recommendedInvestigation() : List.of(),
                    parsed.historicalReferences() != null ? parsed.historicalReferences() : List.of(),
                    parsed.uncertaintyNotes() != null ? parsed.uncertaintyNotes() : List.of(),
                    metadata
            );
        } catch (Exception e) {
            log.error("Failed to parse LLM RCA JSON response: {}. Raw response snippet: {}",
                    e.getMessage(), rawResponse.length() > 200 ? rawResponse.substring(0, 200) + "..." : rawResponse);

            return createFallbackReport(context, latency, e.getMessage());
        }
    }

    private String extractJsonPayload(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }
        String trimmed = response.trim();

        // Handle ```json ... ```
        if (trimmed.contains("```json")) {
            int start = trimmed.indexOf("```json") + 7;
            int end = trimmed.lastIndexOf("```");
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
        } else if (trimmed.contains("```")) {
            int start = trimmed.indexOf("```") + 3;
            int end = trimmed.lastIndexOf("```");
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
        }

        int firstBrace = trimmed.indexOf("{");
        int lastBrace = trimmed.lastIndexOf("}");
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }

        return trimmed;
    }

    private RcaReport createFallbackReport(RcaContext context, long latency, String errorMessage) {
        String rootSvc = (context.summary() != null && context.summary().primaryService() != null)
                ? context.summary().primaryService()
                : "unknown";
        String incidentIdStr = (context.summary() != null && context.summary().incidentId() != null)
                ? context.summary().incidentId()
                : "unknown";

        RcaValidationResult fallbackValidation = RcaValidationResult.invalid(
                RcaValidationResult.RcaValidationStatus.MALFORMED_JSON,
                List.of("Model response was not valid JSON: " + errorMessage),
                List.of(),
                List.of("Fallback report generated")
        );

        return new RcaReport(
                new RcaReport.RootCause(
                        "Fallback: Root cause analysis failed to parse model output",
                        "UNKNOWN",
                        rootSvc,
                        "Parser error: " + errorMessage,
                        false
                ),
                new RcaReport.Confidence("LOW", 0.2, "Low confidence due to parsing fallback"),
                List.of(),
                List.of(),
                new RcaReport.AffectedServices(rootSvc, List.of(), Map.of(rootSvc, "Impact unconfirmed due to parsing error")),
                List.of(new RcaReport.RecommendedInvestigation("Inspect raw telemetry and rerun RCA analysis", "HIGH", "Parsing fallback triggered", "RB-001")),
                List.of(),
                List.of("Model response was not valid JSON: " + errorMessage),
                new RcaReport.RcaReportMetadata(Instant.now(), llmProvider.getProviderName(), llmProvider.getModelName(), latency, incidentIdStr),
                fallbackValidation
        );
    }
}
