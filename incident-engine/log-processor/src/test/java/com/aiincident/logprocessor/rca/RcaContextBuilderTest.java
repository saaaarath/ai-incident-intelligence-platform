package com.aiincident.logprocessor.rca;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.dependency.ServiceDependency;
import com.aiincident.logprocessor.dependency.ServiceDependencyService;
import com.aiincident.logprocessor.dependency.ServiceDependencyService.ServiceTopology;
import com.aiincident.logprocessor.dependency.ServiceDependencyType;
import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.historical.embedding.IncidentRetrievalService;
import com.aiincident.logprocessor.historical.embedding.SemanticSearchResult;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentEvidence;
import com.aiincident.logprocessor.incident.IncidentEvidenceRepository;
import com.aiincident.logprocessor.incident.IncidentRepository;
import com.aiincident.logprocessor.incident.IncidentStatus;
import com.aiincident.logprocessor.metrics.LatencyMetrics;
import com.aiincident.logprocessor.metrics.MetricsAggregationService;
import com.aiincident.logprocessor.metrics.OperationalMetrics;
import com.aiincident.logprocessor.repository.LogEventRepository;
import com.aiincident.logprocessor.timeline.IncidentTimeline;
import com.aiincident.logprocessor.timeline.IncidentTimelineService;
import com.aiincident.logprocessor.timeline.TimelineEvent;
import com.aiincident.logprocessor.timeline.TimelineEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RcaContextBuilderTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentEvidenceRepository evidenceRepository;

    @Mock
    private IncidentTimelineService timelineService;

    @Mock
    private LogEventRepository logEventRepository;

    @Mock
    private MetricsAggregationService metricsService;

    @Mock
    private ServiceDependencyService dependencyService;

    @Mock
    private PrimaryFailureAnalyzer primaryFailureAnalyzer;

    @Mock
    private IncidentRetrievalService incidentRetrievalService;

    private RcaContextBuilder builder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        builder = new RcaContextBuilder(
                incidentRepository,
                evidenceRepository,
                timelineService,
                logEventRepository,
                metricsService,
                dependencyService,
                primaryFailureAnalyzer,
                incidentRetrievalService,
                objectMapper
        );
    }

    private Incident createSampleIncident() {
        Instant t0 = Instant.parse("2026-08-23T12:00:00Z");
        Incident incident = new Incident(
                "Database Connection Pool Exhaustion on payment-service",
                AnomalySeverity.CRITICAL,
                IncidentStatus.OPEN,
                "payment-service",
                t0,
                t0,
                "HikariPool-1 connections exhausted resulting in database timeouts",
                "database.connection.timeout"
        );
        incident.setId(10L);
        incident.setIncidentId("INC-TEST-100");
        incident.setRootService("payment-service");
        incident.setAffectedServices(Set.of("payment-service", "order-service"));
        return incident;
    }

    @Test
    @DisplayName("Should build complete RCA context with all 8 evidence components")
    void testBuildCompleteRcaContext() {
        Incident incident = createSampleIncident();
        Instant t0 = incident.getStartedAt();

        // 1. Evidence
        IncidentEvidence ev1 = new IncidentEvidence(10L, "ev-1", t0, "payment-service", "DB_TIMEOUT",
                AnomalySeverity.CRITICAL, "Payment DB connection timeout", "tr-100", "{\"errorCode\":\"TIMEOUT\"}");
        IncidentEvidence ev2 = new IncidentEvidence(10L, "ev-2", t0.plusSeconds(5), "order-service", "SERVICE_UNAVAILABLE",
                AnomalySeverity.HIGH, "Order downstream payment 503", "tr-100", "{\"statusCode\":503}");
        incident.setEvidence(List.of(ev1, ev2));

        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));
        when(evidenceRepository.findByIncidentIdOrderByTimestampAsc(10L)).thenReturn(List.of(ev1, ev2));

        // 2. Synthesized summary
        when(incidentRetrievalService.synthesizeIncidentSummary(any(), any()))
                .thenReturn("Synthesized summary: Payment service connection pool exhaustion causing cascading order failures.");

        // 3. Timeline
        IncidentTimeline mockTimeline = new IncidentTimeline(
                10L, incident.getTitle(), "payment-service", "payment-service",
                t0.minusSeconds(300), t0.plusSeconds(300), 2,
                List.of(
                        new TimelineEvent("tl-1", t0, TimelineEventType.SERVICE_FAILURE, "payment-service", "DB_TIMEOUT", "DB timeout", "CRITICAL", "ev-1", Map.of()),
                        new TimelineEvent("tl-2", t0.plusSeconds(5), TimelineEventType.SERVICE_FAILURE, "order-service", "SERVICE_UNAVAILABLE", "Downstream 503", "HIGH", "ev-2", Map.of())
                ),
                "Timeline with 2 events"
        );
        when(timelineService.buildTimelineForIncident(eq(incident), anyInt(), any())).thenReturn(mockTimeline);

        // 4. Logs
        ProcessedLogEvent log1 = new ProcessedLogEvent("ev-1", t0, "payment-service", "ERROR", "DB_TIMEOUT", "tr-100", "DB timeout occurred", "{\"pool\":\"Hikari\"}", Instant.now());
        ProcessedLogEvent log2 = new ProcessedLogEvent("ev-2", t0.plusSeconds(5), "order-service", "ERROR", "SERVICE_UNAVAILABLE", "tr-100", "Payment 503 error", "{}", Instant.now());
        ProcessedLogEvent logUnrelated = new ProcessedLogEvent("ev-unrelated", t0.plusSeconds(2), "notification-service", "INFO", "EMAIL_SENT", "tr-999", "Email sent successfully", "{}", Instant.now());
        when(logEventRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of(log1, logUnrelated, log2));

        // 5. Metrics
        OperationalMetrics omPayment = new OperationalMetrics("payment-service", t0.minusSeconds(300), t0.plusSeconds(300),
                100, 20, 0.20, new LatencyMetrics(100, 10.0, 5000.0, 1200.0, 800.0, 3500.0, 4800.0));
        OperationalMetrics omOrder = new OperationalMetrics("order-service", t0.minusSeconds(300), t0.plusSeconds(300),
                200, 15, 0.075, new LatencyMetrics(200, 5.0, 2000.0, 450.0, 200.0, 1500.0, 1900.0));
        when(metricsService.getSummary(eq("payment-service"), any(), any())).thenReturn(omPayment);
        when(metricsService.getSummary(eq("order-service"), any(), any())).thenReturn(omOrder);

        // 6. Dependencies
        when(dependencyService.getServiceTopology("payment-service"))
                .thenReturn(new ServiceTopology("payment-service", Set.of("postgres"), Set.of("order-service"), Set.of("order-service", "postgres"), List.of()));
        when(dependencyService.getServiceTopology("order-service"))
                .thenReturn(new ServiceTopology("order-service", Set.of("payment-service"), Set.of(), Set.of("payment-service"), List.of()));
        when(dependencyService.getAllDependencies())
                .thenReturn(List.of(new ServiceDependency("order-service", "payment-service", ServiceDependencyType.HTTP_REST, "HIGH", "order to payment")));

        // 7. Primary Failure
        PrimaryFailureCandidate candidate = new PrimaryFailureCandidate("payment-service", 95.0, "HIGH", true, false,
                t0, t0, 1, "DB_TIMEOUT", AnomalySeverity.CRITICAL, Map.of("temporalPrecedence", 40.0), List.of("First failure observed"), List.of("order-service"));
        PrimaryFailureAnalysis primaryFailure = new PrimaryFailureAnalysis(10L, candidate, List.of(candidate), List.of(), Instant.now(), "Payment service is primary root cause");
        when(primaryFailureAnalyzer.analyzeIncident(incident)).thenReturn(primaryFailure);

        // 8. Historical & Runbooks
        SemanticSearchResult similarInc = new SemanticSearchResult();
        similarInc.setDocumentId("HIST-001");
        similarInc.setSimilarityScore(0.94);
        similarInc.setContent("Past PostgreSQL connection pool exhaustion postmortem");
        when(incidentRetrievalService.findSimilarIncidents(anyString(), anyInt())).thenReturn(List.of(similarInc));

        SemanticSearchResult runbook = new SemanticSearchResult();
        runbook.setDocumentId("RB-001");
        runbook.setSimilarityScore(0.91);
        runbook.setContent("Database Connection Pool Exhaustion Mitigation Runbook");
        when(incidentRetrievalService.findRelevantRunbooks(anyString(), anyInt())).thenReturn(List.of(runbook));

        // Execute build
        Optional<RcaContext> contextOpt = builder.buildContext(10L, RcaContextBuilder.RcaContextOptions.defaults());

        assertThat(contextOpt).isPresent();
        RcaContext context = contextOpt.get();

        // Verify 1. Summary
        assertThat(context.summary()).isNotNull();
        assertThat(context.summary().id()).isEqualTo(10L);
        assertThat(context.summary().incidentId()).isEqualTo("INC-TEST-100");
        assertThat(context.summary().primaryService()).isEqualTo("payment-service");
        assertThat(context.summary().affectedServices()).containsExactlyInAnyOrder("payment-service", "order-service");
        assertThat(context.summary().synthesizedSummary()).contains("Payment service connection pool exhaustion");

        // Verify 2. Timeline
        assertThat(context.timeline()).isNotNull();
        assertThat(context.timeline().totalEvents()).isEqualTo(2);

        // Verify 3. Relevant Logs (Filtered to exclude unrelated notification-service)
        assertThat(context.relevantLogs()).hasSize(2);
        assertThat(context.relevantLogs().stream().map(RcaContext.RelevantLogEntry::service).toList())
                .containsExactlyInAnyOrder("payment-service", "order-service");
        assertThat(context.relevantLogs().stream().map(RcaContext.RelevantLogEntry::service).toList())
                .doesNotContain("notification-service");

        // Verify 4. Metrics
        assertThat(context.metrics()).hasSize(2);
        RcaContext.ServiceMetricsSummary paymentMetrics = context.metrics().stream()
                .filter(m -> m.service().equals("payment-service")).findFirst().orElseThrow();
        assertThat(paymentMetrics.totalRequests()).isEqualTo(100);
        assertThat(paymentMetrics.errorCount()).isEqualTo(20);
        assertThat(paymentMetrics.errorRate()).isEqualTo(0.20);
        assertThat(paymentMetrics.avgLatencyMs()).isEqualTo(1200.0);

        // Verify 5. Dependencies
        assertThat(context.dependencies()).isNotNull();
        assertThat(context.dependencies().upstreamCallers()).contains("order-service");
        assertThat(context.dependencies().downstreamDependencies()).contains("postgres", "payment-service");
        assertThat(context.dependencies().directDependencies()).hasSize(1);

        // Verify 6. Primary Failure Analysis
        assertThat(context.primaryFailure()).isNotNull();
        assertThat(context.primaryFailure().primaryCandidate().service()).isEqualTo("payment-service");
        assertThat(context.primaryFailure().primaryCandidate().isPrimary()).isTrue();

        // Verify 7. Similar Historical Incidents
        assertThat(context.similarHistoricalIncidents()).hasSize(1);
        assertThat(context.similarHistoricalIncidents().get(0).getDocumentId()).isEqualTo("HIST-001");

        // Verify 8. Relevant Runbooks
        assertThat(context.relevantRunbooks()).hasSize(1);
        assertThat(context.relevantRunbooks().get(0).getDocumentId()).isEqualTo("RB-001");

        // Verify Metadata
        assertThat(context.metadata()).isNotNull();
        assertThat(context.metadata().totalLogsConsidered()).isEqualTo(3);
        assertThat(context.metadata().relevantLogsIncluded()).isEqualTo(2);
        assertThat(context.metadata().timelineEventsCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should strictly exclude unrelated excessive data and limit log count")
    void testLogFilteringAndBounding() {
        Incident incident = createSampleIncident();
        Instant t0 = incident.getStartedAt();
        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));

        // Create 20 logs: 10 from payment-service (5 errors, 5 info) and 10 from unrelated auth-service
        List<ProcessedLogEvent> logs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            logs.add(new ProcessedLogEvent("err-" + i, t0.plusSeconds(i), "payment-service", "ERROR", "DB_ERROR", "tr-1", "DB err " + i, "{}", Instant.now()));
        }
        for (int i = 0; i < 5; i++) {
            logs.add(new ProcessedLogEvent("info-" + i, t0.plusSeconds(10 + i), "payment-service", "INFO", "METRIC_LOG", "tr-1", "Payment info " + i, "{}", Instant.now()));
        }
        for (int i = 0; i < 10; i++) {
            logs.add(new ProcessedLogEvent("auth-" + i, t0.plusSeconds(i), "auth-service", "ERROR", "AUTH_FAIL", "tr-unrelated", "Auth fail " + i, "{}", Instant.now()));
        }
        when(logEventRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any())).thenReturn(logs);

        // Limit to 3 logs max
        RcaContextBuilder.RcaContextOptions options = new RcaContextBuilder.RcaContextOptions(5, 3, 3, 3);
        Optional<RcaContext> contextOpt = builder.buildContext(10L, options);

        assertThat(contextOpt).isPresent();
        RcaContext context = contextOpt.get();

        // Only 3 logs should be returned
        assertThat(context.relevantLogs()).hasSize(3);
        // All returned logs should only be from payment-service and must be ERROR level (prioritized)
        for (RcaContext.RelevantLogEntry logEntry : context.relevantLogs()) {
            assertThat(logEntry.service()).isEqualTo("payment-service");
            assertThat(logEntry.level()).isEqualTo("ERROR");
        }
        // Total logs considered should be 20, but relevantLogsIncluded is 3
        assertThat(context.metadata().totalLogsConsidered()).isEqualTo(20);
        assertThat(context.metadata().relevantLogsIncluded()).isEqualTo(3);
    }
}
