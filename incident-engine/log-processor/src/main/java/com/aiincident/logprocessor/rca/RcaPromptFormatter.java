package com.aiincident.logprocessor.rca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/**
 * Formats system instructions and user evidence prompts for the AI RCA Engine.
 * Ensures the LLM receives ONLY structured evidence and enforces domain boundaries.
 */
@Component
public class RcaPromptFormatter {

    private final ObjectMapper objectMapper;

    public RcaPromptFormatter() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public RcaPromptFormatter(ObjectMapper objectMapper) {
        this.objectMapper = (objectMapper != null)
                ? objectMapper.copy().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                : new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Formats the strict system prompt for AI Root Cause Analysis.
     */
    public String formatSystemPrompt() {
        return """
                You are an Expert AI Site Reliability Engineer (AI SRE) executing an automated Root Cause Analysis (RCA).
                
                You must analyze ONLY the structured evidence provided in the context. Do not invent or assume telemetry that is not in the evidence.
                
                CRITICAL REQUIREMENTS:
                1. Distinguish between:
                   - DIRECT OBSERVED EVIDENCE: Concrete facts explicitly recorded in logs, metric thresholds, deployment events, or timeline events.
                   - INFERENCE: Logical deductions based on dependency topology and cascading symptom chains.
                   - UNCERTAINTY: Telemetry gaps, missing logs, ambiguous timestamps, or unverified assumptions.
                2. Confidence calibration:
                   - You MUST NOT claim HIGH confidence unless direct root-cause failure events are observed in the evidence.
                   - If evidence is sparse or root causes are inferred from downstream symptoms, assign MEDIUM or LOW confidence and document uncertainty notes.
                3. Grounded recommendations:
                   - Do not provide arbitrary, generic, or unsupported recommendations.
                   - All recommended investigation and remediation actions MUST be directly justified by the observed evidence or attached runbooks.
                4. Alternative hypotheses:
                   - Evaluate at least one alternative hypothesis and provide concrete reasons why it was rejected or what missing evidence would test it.
                5. Output format:
                   - Return ONLY a single, valid JSON object conforming strictly to the following schema:
                   {
                     "rootCause": {
                       "statement": "Clear root cause statement",
                       "category": "FAILURE_CATEGORY",
                       "rootService": "service-name",
                       "inferenceDetails": "Explanation distinguishing observed facts from topological inferences",
                       "isDirectlyObserved": true/false
                     },
                     "confidence": {
                       "level": "HIGH" | "MEDIUM" | "LOW",
                       "score": 0.0 to 1.0,
                       "rationale": "Reasoning for confidence score based on evidence completeness"
                     },
                     "evidence": [
                       {
                         "type": "LOG" | "METRIC" | "ANOMALY" | "DEPLOYMENT" | "TOPOLOGY",
                         "service": "service-name",
                         "observation": "Specific observed evidence description",
                         "sourceId": "event-id or metric name",
                         "isDirectObservation": true/false,
                         "timestamp": "ISO-8601 timestamp"
                       }
                     ],
                     "alternativeHypotheses": [
                       {
                         "hypothesis": "Alternative failure scenario considered",
                         "likelihood": "LOW" | "MEDIUM" | "HIGH",
                         "reasonsForRejection": "Why rejected based on evidence",
                         "missingEvidence": "Telemetry that would confirm or refute"
                       }
                     ],
                     "affectedServices": {
                       "rootService": "root-service-name",
                       "symptomServices": ["downstream-service-1"],
                       "serviceImpacts": {
                         "service-name": "Impact description"
                       }
                     },
                     "recommendedInvestigation": [
                       {
                         "action": "Specific investigation or remediation action",
                         "priority": "IMMEDIATE" | "HIGH" | "MEDIUM" | "LOW",
                         "justification": "Evidence or runbook justification",
                         "runbookRef": "Runbook ID or reference"
                       }
                     ],
                     "historicalReferences": [
                       {
                         "referenceId": "HIST-001 or RB-001",
                         "title": "Document title",
                         "similarityReason": "Relevance explanation",
                         "resolutionSummary": "Past resolution summary"
                       }
                     ],
                     "uncertaintyNotes": [
                       "List of telemetry gaps or unresolved questions"
                     ]
                   }
                """;
    }

    /**
     * Formats the user prompt containing ONLY the structured RCA context.
     */
    public String formatUserPrompt(RcaContext context) {
        try {
            String contextJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
            return "Here is the structured RCA evidence package for the incident. Analyze this evidence and produce the structured RCA report JSON:\n\n" + contextJson;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize RcaContext to JSON: " + e.getMessage(), e);
        }
    }
}
