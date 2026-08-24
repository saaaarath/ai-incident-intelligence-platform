package com.aiincident.logprocessor.rca;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.dependency.ServiceDependency;
import com.aiincident.logprocessor.dependency.ServiceDependencyService.ServiceTopology;
import com.aiincident.logprocessor.dependency.ServiceDependencyType;
import com.aiincident.logprocessor.historical.embedding.SemanticSearchResult;
import com.aiincident.logprocessor.incident.IncidentStatus;
import com.aiincident.logprocessor.timeline.IncidentTimeline;
import com.aiincident.logprocessor.timeline.TimelineEvent;
import com.aiincident.logprocessor.timeline.TimelineEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LlmRcaEngineTest {

    @Mock
    private RcaContextBuilder contextBuilder;

    private LlmProvider llmProvider;
    private RcaPromptFormatter promptFormatter;
    private LlmRcaEngine rcaEngine;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        llmProvider = new DeterministicMockLlmProvider("mock-sre-engine");
        promptFormatter = new RcaPromptFormatter(objectMapper);
        rcaEngine = new LlmRcaEngine(llmProvider, contextBuilder, promptFormatter, objectMapper);
    }

    private RcaContext createSampleContext() {
        Instant t0 = Instant.parse("2026-08-23T12:00:00Z");

        RcaContext.IncidentSummary summary = new RcaContext.IncidentSummary(
                10L,
                "INC-TEST-100",
                "Database Connection Pool Exhaustion on payment-service",
                AnomalySeverity.CRITICAL,
                IncidentStatus.OPEN,
                "payment-service",
                "payment-service",
                Set.of("payment-service", "order-service"),
                t0,
                t0,
                null,
                "HikariPool-1 connections exhausted resulting in database timeouts",
                "database.connection.timeout",
                "fp-12345",
                "Synthesized: Payment service connection pool exhaustion causing downstream order failure."
        );

        IncidentTimeline timeline = new IncidentTimeline(
                10L, summary.title(), "payment-service", "payment-service",
                t0.minusSeconds(300), t0.plusSeconds(300), 2,
                List.of(
                        new TimelineEvent("tl-1", t0, TimelineEventType.SERVICE_FAILURE, "payment-service", "DB_TIMEOUT", "DB timeout", "CRITICAL", "ev-1", Map.of()),
                        new TimelineEvent("tl-2", t0.plusSeconds(5), TimelineEventType.SERVICE_FAILURE, "order-service", "SERVICE_UNAVAILABLE", "Downstream 503", "HIGH", "ev-2", Map.of())
                ),
                "Timeline with 2 events"
        );

        List<RcaContext.RelevantLogEntry> logs = List.of(
                new RcaContext.RelevantLogEntry("ev-1", t0, "payment-service", "ERROR", "DB_TIMEOUT", "tr-100", "HikariPool-1 - Connection is not available, request timed out after 3000ms", Map.of("pool", "HikariPool-1")),
                new RcaContext.RelevantLogEntry("ev-2", t0.plusSeconds(5), "order-service", "ERROR", "SERVICE_UNAVAILABLE", "tr-100", "Payment service returned 503 Service Unavailable", Map.of())
        );

        List<RcaContext.ServiceMetricsSummary> metrics = List.of(
                new RcaContext.ServiceMetricsSummary("payment-service", t0.minusSeconds(300), t0.plusSeconds(300), 100, 20, 0.20, 1200.0, 800.0, 3500.0, 4800.0, 5000.0),
                new RcaContext.ServiceMetricsSummary("order-service", t0.minusSeconds(300), t0.plusSeconds(300), 200, 15, 0.075, 450.0, 200.0, 1500.0, 1900.0, 2000.0)
        );

        RcaContext.DependencyContext dependencies = new RcaContext.DependencyContext(
                List.of(
                        new ServiceTopology("payment-service", Set.of("postgres"), Set.of("order-service"), Set.of("order-service", "postgres"), List.of()),
                        new ServiceTopology("order-service", Set.of("payment-service"), Set.of(), Set.of("payment-service"), List.of())
                ),
                List.of(new ServiceDependency("order-service", "payment-service", ServiceDependencyType.HTTP_REST, "HIGH", "order calls payment")),
                Set.of("order-service"),
                Set.of("postgres")
        );

        PrimaryFailureCandidate candidate = new PrimaryFailureCandidate("payment-service", 95.0, "HIGH", true, false,
                t0, t0, 1, "DB_TIMEOUT", AnomalySeverity.CRITICAL, Map.of("temporalPrecedence", 40.0), List.of("First failure observed"), List.of("order-service"));
        PrimaryFailureAnalysis primaryFailure = new PrimaryFailureAnalysis(10L, candidate, List.of(candidate), List.of(), Instant.now(), "Payment service root cause");

        SemanticSearchResult similarInc = new SemanticSearchResult();
        similarInc.setDocumentId("HIST-INC-001");
        similarInc.setSimilarityScore(0.94);
        similarInc.setContent("Title: PostgreSQL Connection Pool Exhaustion on Payment Service\nPostmortem details...");

        SemanticSearchResult runbook = new SemanticSearchResult();
        runbook.setDocumentId("RB-001");
        runbook.setSimilarityScore(0.91);
        runbook.setContent("Title: Database Connection Pool Exhaustion Mitigation Runbook\nSteps...");

        RcaContext.RcaContextMetadata metadata = new RcaContext.RcaContextMetadata(
                Instant.now(), t0.minusSeconds(300), t0.plusSeconds(300), 5, 2, 2, 2, 1, 1
        );

        return new RcaContext(
                summary, timeline, logs, metrics, dependencies, primaryFailure,
                List.of(similarInc), List.of(runbook), metadata
        );
    }

    @Test
    @DisplayName("Should generate complete, structured RCA report from RcaContext")
    void testAnalyzeContextProducesStructuredRcaReport() {
        RcaContext context = createSampleContext();

        RcaReport report = rcaEngine.analyzeContext(context);

        assertThat(report).isNotNull();

        // 1. Root Cause
        assertThat(report.rootCause()).isNotNull();
        assertThat(report.rootCause().rootService()).isEqualTo("payment-service");
        assertThat(report.rootCause().category()).contains("DATABASE");
        assertThat(report.rootCause().statement()).contains("Database connection pool exhaustion");
        assertThat(report.rootCause().inferenceDetails()).contains("Observed Evidence");
        assertThat(report.rootCause().inferenceDetails()).contains("Inference");
        assertThat(report.rootCause().isDirectlyObserved()).isTrue();

        // 2. Confidence
        assertThat(report.confidence()).isNotNull();
        assertThat(report.confidence().level()).isEqualTo("HIGH");
        assertThat(report.confidence().score()).isGreaterThanOrEqualTo(0.9);
        assertThat(report.confidence().rationale()).contains("payment-service");

        // 3. Evidence distinction
        assertThat(report.evidence()).isNotEmpty();
        assertThat(report.evidence().stream().anyMatch(e -> e.type().equals("LOG") && e.isDirectObservation())).isTrue();

        // 4. Alternative Hypotheses
        assertThat(report.alternativeHypotheses()).isNotEmpty();
        RcaReport.AlternativeHypothesis alt = report.alternativeHypotheses().get(0);
        assertThat(alt.hypothesis()).isNotEmpty();
        assertThat(alt.reasonsForRejection()).isNotEmpty();
        assertThat(alt.missingEvidence()).isNotEmpty();

        // 5. Affected Services
        assertThat(report.affectedServices()).isNotNull();
        assertThat(report.affectedServices().rootService()).isEqualTo("payment-service");
        assertThat(report.affectedServices().symptomServices()).contains("order-service");
        assertThat(report.affectedServices().serviceImpacts()).containsKey("payment-service");

        // 6. Recommended Investigation (Grounded in evidence & runbook)
        assertThat(report.recommendedInvestigation()).isNotEmpty();
        RcaReport.RecommendedInvestigation rec = report.recommendedInvestigation().get(0);
        assertThat(rec.action()).contains("connection pool");
        assertThat(rec.priority()).isEqualTo("IMMEDIATE");
        assertThat(rec.justification()).isNotEmpty();
        assertThat(rec.runbookRef()).isEqualTo("RB-001");

        // 7. Historical References
        assertThat(report.historicalReferences()).isNotEmpty();
        assertThat(report.historicalReferences().get(0).referenceId()).isEqualTo("HIST-INC-001");

        // 8. Metadata
        assertThat(report.metadata()).isNotNull();
        assertThat(report.metadata().provider()).isEqualTo("mock");
        assertThat(report.metadata().model()).isEqualTo("mock-sre-engine");
        assertThat(report.metadata().incidentIdentifier()).isEqualTo("INC-TEST-100");
    }

    @Test
    @DisplayName("Prompt Formatter: Prompt receives ONLY structured context and explicit RCA instructions")
    void testPromptFormattingIntegrity() {
        RcaContext context = createSampleContext();

        String systemPrompt = promptFormatter.formatSystemPrompt();
        String userPrompt = promptFormatter.formatUserPrompt(context);

        // System prompt contains domain constraints
        assertThat(systemPrompt).contains("DIRECT OBSERVED EVIDENCE");
        assertThat(systemPrompt).contains("INFERENCE");
        assertThat(systemPrompt).contains("UNCERTAINTY");
        assertThat(systemPrompt).contains("Confidence calibration");
        assertThat(systemPrompt).contains("Grounded recommendations");

        // User prompt contains serialized context JSON
        assertThat(userPrompt).contains("INC-TEST-100");
        assertThat(userPrompt).contains("payment-service");
        assertThat(userPrompt).contains("HikariPool-1");
        assertThat(userPrompt).contains("HIST-INC-001");
        assertThat(userPrompt).contains("RB-001");
    }

    @Test
    @DisplayName("Uncertainty & Calibration: Sparse evidence results in LOW/MEDIUM confidence with uncertainty notes")
    void testSparseEvidenceCalibration() {
        Instant t0 = Instant.parse("2026-08-23T12:00:00Z");

        // Sparse context with no direct failure logs
        RcaContext.IncidentSummary summary = new RcaContext.IncidentSummary(
                20L, "INC-SPARSE", "High Latency Warning", AnomalySeverity.LOW,
                IncidentStatus.OPEN, "order-service", "order-service",
                Set.of("order-service"), t0, t0, null, "Intermittent latency", "latency", "fp-sparse", "Sparse summary"
        );

        RcaContext sparseContext = new RcaContext(
                summary,
                new IncidentTimeline(20L, "High Latency Warning", "order-service", "order-service", t0, t0, 0, List.of(), "Empty timeline"),
                List.of(), // No logs
                List.of(),
                new RcaContext.DependencyContext(List.of(), List.of(), Set.of(), Set.of()),
                null,
                List.of(),
                List.of(),
                new RcaContext.RcaContextMetadata(Instant.now(), t0, t0, 5, 0, 0, 0, 0, 0)
        );

        RcaReport report = rcaEngine.analyzeContext(sparseContext);

        assertThat(report.confidence().level()).isEqualTo("LOW");
        assertThat(report.confidence().score()).isLessThanOrEqualTo(0.5);
        assertThat(report.uncertaintyNotes()).isNotEmpty();
        assertThat(report.rootCause().isDirectlyObserved()).isFalse();
    }
}
