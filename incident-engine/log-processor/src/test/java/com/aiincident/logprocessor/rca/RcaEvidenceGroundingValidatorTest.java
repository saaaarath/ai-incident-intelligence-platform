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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RcaEvidenceGroundingValidatorTest {

    private RcaEvidenceGroundingValidator validator;
    private RcaContext sampleContext;

    @BeforeEach
    void setUp() {
        validator = new RcaEvidenceGroundingValidator();

        Instant t0 = Instant.parse("2026-08-23T12:00:00Z");

        RcaContext.IncidentSummary summary = new RcaContext.IncidentSummary(
                1L,
                "INC-1",
                "Database Timeout Incident",
                AnomalySeverity.CRITICAL,
                IncidentStatus.OPEN,
                "payment-service",
                "payment-service",
                Set.of("payment-service", "order-service"),
                t0,
                t0,
                null,
                "Hikari connection pool timeout on payment-service",
                "database.connection.timeout",
                "fp-123",
                "Payment service DB timeout"
        );

        IncidentTimeline timeline = new IncidentTimeline(
                1L, summary.title(), "payment-service", "payment-service",
                t0.minusSeconds(60), t0.plusSeconds(60), 2,
                List.of(
                        new TimelineEvent("tl-1", t0, TimelineEventType.SERVICE_FAILURE, "payment-service", "DB_TIMEOUT", "DB timeout", "CRITICAL", "ev-1", Map.of()),
                        new TimelineEvent("tl-2", t0.plusSeconds(5), TimelineEventType.SERVICE_FAILURE, "order-service", "SERVICE_UNAVAILABLE", "503 error", "HIGH", "ev-2", Map.of())
                ),
                "Timeline"
        );

        List<RcaContext.RelevantLogEntry> logs = List.of(
                new RcaContext.RelevantLogEntry("ev-1", t0, "payment-service", "ERROR", "DB_TIMEOUT", "tr-1", "HikariPool timeout", Map.of()),
                new RcaContext.RelevantLogEntry("ev-2", t0.plusSeconds(5), "order-service", "ERROR", "SERVICE_UNAVAILABLE", "tr-1", "503 error", Map.of())
        );

        List<RcaContext.ServiceMetricsSummary> metrics = List.of(
                new RcaContext.ServiceMetricsSummary("payment-service", t0.minusSeconds(60), t0.plusSeconds(60), 100, 20, 0.20, 1200.0, 800.0, 2500.0, 3000.0, 3200.0)
        );

        RcaContext.DependencyContext dependencies = new RcaContext.DependencyContext(
                List.of(new ServiceTopology("payment-service", Set.of(), Set.of("order-service"), Set.of(), List.of())),
                List.of(new ServiceDependency("order-service", "payment-service", ServiceDependencyType.HTTP_REST, "HIGH", "order calls payment")),
                Set.of("order-service"),
                Set.of()
        );

        PrimaryFailureCandidate candidate = new PrimaryFailureCandidate("payment-service", 90.0, "HIGH", true, false,
                t0, t0, 1, "DB_TIMEOUT", AnomalySeverity.CRITICAL, Map.of(), List.of("First"), List.of("order-service"));
        PrimaryFailureAnalysis primaryFailure = new PrimaryFailureAnalysis(1L, candidate, List.of(candidate), List.of(), Instant.now(), "Payment primary");

        SemanticSearchResult similarInc = new SemanticSearchResult();
        similarInc.setDocumentId("HIST-INC-001");
        similarInc.setTitle("Postgres Pool Exhaustion");

        SemanticSearchResult runbook = new SemanticSearchResult();
        runbook.setDocumentId("RB-001");
        runbook.setTitle("Database Connection Pool Mitigation");

        sampleContext = new RcaContext(
                summary, timeline, logs, metrics, dependencies, primaryFailure,
                List.of(similarInc), List.of(runbook),
                new RcaContext.RcaContextMetadata(Instant.now(), t0.minusSeconds(60), t0.plusSeconds(60), 2, 2, 1, 1, 1, 1)
        );
    }

    @Test
    @DisplayName("Valid report should pass schema and grounding verification")
    void testValidate_ValidReport() {
        RcaReport report = new RcaReport(
                new RcaReport.RootCause("Hikari pool exhaustion on payment-service", "DATABASE_CONNECTION_EXHAUSTION", "payment-service", "Observed timeout", true),
                new RcaReport.Confidence("HIGH", 0.95, "Observed direct log ev-1 on root service"),
                List.of(
                        new RcaReport.EvidenceItem("LOG", "payment-service", "HikariPool timeout", "ev-1", true, Instant.now()),
                        new RcaReport.EvidenceItem("LOG", "order-service", "503 error", "ev-2", true, Instant.now())
                ),
                List.of(new RcaReport.AlternativeHypothesis("Network glitch", "LOW", "Explicit DB logs present", "None")),
                new RcaReport.AffectedServices("payment-service", List.of("order-service"), Map.of("payment-service", "DB timeout", "order-service", "503")),
                List.of(new RcaReport.RecommendedInvestigation("Check Hikari pool size", "HIGH", "Direct log match", "RB-001")),
                List.of(new RcaReport.HistoricalReference("HIST-INC-001", "Postgres Pool Exhaustion", "Same pool issue", "Scaled pool")),
                List.of(),
                new RcaReport.RcaReportMetadata(Instant.now(), "mock", "mock-sre-engine", 100, "INC-1")
        );

        RcaValidationResult result = validator.validate(report, sampleContext);

        assertThat(result.isValid()).isTrue();
        assertThat(result.isGrounded()).isTrue();
        assertThat(result.status()).isEqualTo(RcaValidationResult.RcaValidationStatus.VALID);
        assertThat(result.errors()).isEmpty();
        assertThat(result.groundingViolations()).isEmpty();
    }

    @Test
    @DisplayName("Report with fabricated log eventId should fail grounding")
    void testValidate_FabricatedLogEventId() {
        RcaReport report = new RcaReport(
                new RcaReport.RootCause("Hikari pool exhaustion", "DATABASE_CONNECTION_EXHAUSTION", "payment-service", "Observed timeout", true),
                new RcaReport.Confidence("HIGH", 0.95, "Observed log"),
                List.of(new RcaReport.EvidenceItem("LOG", "payment-service", "Fabricated error", "ev-hallucinated-999", true, Instant.now())),
                List.of(),
                new RcaReport.AffectedServices("payment-service", List.of("order-service"), Map.of()),
                List.of(),
                List.of(),
                List.of(),
                new RcaReport.RcaReportMetadata(Instant.now(), "mock", "mock-sre-engine", 100, "INC-1")
        );

        RcaValidationResult result = validator.validate(report, sampleContext);

        assertThat(result.isValid()).isFalse();
        assertThat(result.isGrounded()).isFalse();
        assertThat(result.status()).isEqualTo(RcaValidationResult.RcaValidationStatus.UNGROUNDED);
        assertThat(result.groundingViolations()).anyMatch(v -> v.contains("ev-hallucinated-999"));
    }

    @Test
    @DisplayName("Report citing ungrounded runbook reference should fail grounding")
    void testValidate_FabricatedRunbookRef() {
        RcaReport report = new RcaReport(
                new RcaReport.RootCause("Hikari pool exhaustion", "DATABASE_CONNECTION_EXHAUSTION", "payment-service", "Observed timeout", true),
                new RcaReport.Confidence("HIGH", 0.95, "Observed log"),
                List.of(new RcaReport.EvidenceItem("LOG", "payment-service", "HikariPool timeout", "ev-1", true, Instant.now())),
                List.of(),
                new RcaReport.AffectedServices("payment-service", List.of("order-service"), Map.of()),
                List.of(new RcaReport.RecommendedInvestigation("Scale Kubernetes cluster", "HIGH", "Arbitrary", "RB-K8S-HALLUCINATED-999")),
                List.of(new RcaReport.HistoricalReference("HIST-NONEXISTENT-999", "Fake Title", "Fake", "Fake")),
                List.of(),
                new RcaReport.RcaReportMetadata(Instant.now(), "mock", "mock-sre-engine", 100, "INC-1")
        );

        RcaValidationResult result = validator.validate(report, sampleContext);

        assertThat(result.isValid()).isFalse();
        assertThat(result.isGrounded()).isFalse();
        assertThat(result.status()).isEqualTo(RcaValidationResult.RcaValidationStatus.UNGROUNDED);
        assertThat(result.groundingViolations()).anyMatch(v -> v.contains("RB-K8S-HALLUCINATED-999"));
        assertThat(result.groundingViolations()).anyMatch(v -> v.contains("HIST-NONEXISTENT-999"));
    }

    @Test
    @DisplayName("Report referencing unknown service not in context should fail grounding")
    void testValidate_UnknownService() {
        RcaReport report = new RcaReport(
                new RcaReport.RootCause("Unknown failure", "DATABASE_CONNECTION_EXHAUSTION", "fraud-detection-service", "Inference", false),
                new RcaReport.Confidence("MEDIUM", 0.6, "Inferred"),
                List.of(new RcaReport.EvidenceItem("LOG", "fraud-detection-service", "Fraud timeout", "ev-1", false, Instant.now())),
                List.of(),
                new RcaReport.AffectedServices("fraud-detection-service", List.of("order-service"), Map.of()),
                List.of(),
                List.of(),
                List.of(),
                new RcaReport.RcaReportMetadata(Instant.now(), "mock", "mock-sre-engine", 100, "INC-1")
        );

        RcaValidationResult result = validator.validate(report, sampleContext);

        assertThat(result.isValid()).isFalse();
        assertThat(result.isGrounded()).isFalse();
        assertThat(result.groundingViolations()).anyMatch(v -> v.contains("fraud-detection-service"));
    }

    @Test
    @DisplayName("Report with missing or empty root cause statement should fail schema validation")
    void testValidate_EmptyRootCause() {
        RcaReport report = new RcaReport(
                new RcaReport.RootCause("", "UNKNOWN", "", "No info", false),
                new RcaReport.Confidence("LOW", 0.2, "Low"),
                List.of(),
                List.of(),
                new RcaReport.AffectedServices("payment-service", List.of(), Map.of()),
                List.of(),
                List.of(),
                List.of(),
                new RcaReport.RcaReportMetadata(Instant.now(), "mock", "mock-sre-engine", 100, "INC-1")
        );

        RcaValidationResult result = validator.validate(report, sampleContext);

        assertThat(result.isValid()).isFalse();
        assertThat(result.status()).isEqualTo(RcaValidationResult.RcaValidationStatus.INVALID_SCHEMA);
        assertThat(result.errors()).anyMatch(e -> e.contains("rootCause.statement"));
        assertThat(result.errors()).anyMatch(e -> e.contains("rootCause.rootService"));
    }

    @Test
    @DisplayName("Report with confidence score out of bounds should fail schema validation")
    void testValidate_ConfidenceScoreOutOfBounds() {
        RcaReport report = new RcaReport(
                new RcaReport.RootCause("Valid root cause", "DATABASE_CONNECTION_EXHAUSTION", "payment-service", "Details", true),
                new RcaReport.Confidence("HIGH", 1.5, "Overconfident"),
                List.of(new RcaReport.EvidenceItem("LOG", "payment-service", "HikariPool timeout", "ev-1", true, Instant.now())),
                List.of(),
                new RcaReport.AffectedServices("payment-service", List.of(), Map.of()),
                List.of(),
                List.of(),
                List.of(),
                new RcaReport.RcaReportMetadata(Instant.now(), "mock", "mock-sre-engine", 100, "INC-1")
        );

        RcaValidationResult result = validator.validate(report, sampleContext);

        assertThat(result.isValid()).isFalse();
        assertThat(result.status()).isEqualTo(RcaValidationResult.RcaValidationStatus.INVALID_SCHEMA);
        assertThat(result.errors()).anyMatch(e -> e.contains("confidence.score"));
    }
}
