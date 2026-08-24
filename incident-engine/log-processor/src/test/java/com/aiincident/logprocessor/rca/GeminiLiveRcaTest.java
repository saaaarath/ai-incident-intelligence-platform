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
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GeminiLiveRcaTest {

    @Autowired
    private LlmProperties llmProperties;

    @Autowired
    private RcaPromptFormatter promptFormatter;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Live Gemini Verification: Call Google Gemini API with RcaContext and verify structured RcaReport")
    void testLiveGeminiRcaExecution() {
        String apiKey = llmProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("your_gemini_api_key")) {
            System.out.println("Skipping live Gemini test - no API key configured");
            return;
        }

        System.out.println("Testing live Gemini API with model: gemini-3.6-flash");

        // 1. Configure provider for Gemini
        LlmProperties geminiProps = new LlmProperties();
        geminiProps.setProvider("gemini");
        geminiProps.setModel("gemini-3.6-flash");
        geminiProps.setApiKey(apiKey);
        geminiProps.setApiUrl("https://generativelanguage.googleapis.com/v1beta");
        geminiProps.setTemperature(0.1);
        geminiProps.setMaxTokens(8192);
        geminiProps.setTimeoutMs(30000);

        HttpLlmProvider geminiProvider = new HttpLlmProvider(geminiProps);
        LlmRcaEngine engine = new LlmRcaEngine(geminiProvider, null, promptFormatter, objectMapper);

        // 2. Formulate realistic incident context
        Instant t0 = Instant.parse("2026-08-23T14:00:00Z");

        RcaContext.IncidentSummary summary = new RcaContext.IncidentSummary(
                101L,
                "INC-PROD-101",
                "Database Timeout and Cascading 503 Outage",
                AnomalySeverity.CRITICAL,
                IncidentStatus.OPEN,
                "payment-service",
                "payment-service",
                Set.of("payment-service", "order-service"),
                t0,
                t0,
                null,
                "HikariPool-1 connections saturated on payment-service causing query timeouts and upstream 503s on order-service",
                "database.connection.timeout",
                "fp-db-pool-exhausted",
                "Payment service database pool exhaustion leading to cascading 503 errors on order-service."
        );

        IncidentTimeline timeline = new IncidentTimeline(
                101L, summary.title(), "payment-service", "payment-service",
                t0.minusSeconds(300), t0.plusSeconds(300), 2,
                List.of(
                        new TimelineEvent("tl-1", t0, TimelineEventType.SERVICE_FAILURE, "payment-service", "DB_TIMEOUT", "HikariPool-1 connection timeout", "CRITICAL", "ev-1", Map.of()),
                        new TimelineEvent("tl-2", t0.plusSeconds(5), TimelineEventType.SERVICE_FAILURE, "order-service", "SERVICE_UNAVAILABLE", "Downstream payment-service returned 503", "HIGH", "ev-2", Map.of())
                ),
                "Timeline with 2 failure events"
        );

        List<RcaContext.RelevantLogEntry> logs = List.of(
                new RcaContext.RelevantLogEntry("ev-1", t0, "payment-service", "ERROR", "DB_TIMEOUT", "tr-live-1", "HikariPool-1 - Connection is not available, request timed out after 3000ms", Map.of("pool", "HikariPool-1")),
                new RcaContext.RelevantLogEntry("ev-2", t0.plusSeconds(5), "order-service", "ERROR", "SERVICE_UNAVAILABLE", "tr-live-1", "Payment service returned HTTP 503 Service Unavailable", Map.of("statusCode", 503))
        );

        List<RcaContext.ServiceMetricsSummary> metrics = List.of(
                new RcaContext.ServiceMetricsSummary("payment-service", t0.minusSeconds(300), t0.plusSeconds(300), 120, 25, 0.208, 1450.0, 900.0, 3800.0, 4900.0, 5200.0),
                new RcaContext.ServiceMetricsSummary("order-service", t0.minusSeconds(300), t0.plusSeconds(300), 250, 20, 0.08, 520.0, 250.0, 1800.0, 2100.0, 2300.0)
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
        PrimaryFailureAnalysis primaryFailure = new PrimaryFailureAnalysis(101L, candidate, List.of(candidate), List.of(), Instant.now(), "Payment service is primary root cause");

        SemanticSearchResult similarInc = new SemanticSearchResult();
        similarInc.setDocumentId("HIST-INC-001");
        similarInc.setSimilarityScore(0.95);
        similarInc.setContent("Title: PostgreSQL Connection Pool Exhaustion on Payment Service\nRoot Cause: Slow query held database connections leading to HikariCP pool exhaustion.");

        SemanticSearchResult runbook = new SemanticSearchResult();
        runbook.setDocumentId("RB-001");
        runbook.setSimilarityScore(0.92);
        runbook.setContent("Title: Database Connection Pool Exhaustion Mitigation Runbook\nSteps: Check pool sizing, active queries, and scale connection pool.");

        RcaContext context = new RcaContext(
                summary, timeline, logs, metrics, dependencies, primaryFailure,
                List.of(similarInc), List.of(runbook),
                new RcaContext.RcaContextMetadata(Instant.now(), t0.minusSeconds(300), t0.plusSeconds(300), 5, 2, 2, 2, 1, 1)
        );

        // 3. Execute AI RCA with live Gemini model
        RcaReport report = engine.analyzeContext(context);

        System.out.println("=== LIVE GEMINI RCA REPORT OUTPUT ===");
        try {
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        } catch (Exception ignored) {}

        // 4. Assertions on Gemini response
        assertThat(report).isNotNull();
        assertThat(report.rootCause()).isNotNull();
        assertThat(report.rootCause().rootService()).containsIgnoringCase("payment");
        assertThat(report.confidence()).isNotNull();
        assertThat(report.confidence().level()).isIn("HIGH", "MEDIUM");
        assertThat(report.evidence()).isNotEmpty();
        assertThat(report.alternativeHypotheses()).isNotEmpty();
        assertThat(report.affectedServices()).isNotNull();
        assertThat(report.affectedServices().rootService()).containsIgnoringCase("payment");
        assertThat(report.recommendedInvestigation()).isNotEmpty();
        assertThat(report.historicalReferences()).isNotEmpty();
    }
}
