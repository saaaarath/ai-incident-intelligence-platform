package com.aiincident.logprocessor.rca;

import com.aiincident.logging.LogEvent;
import com.aiincident.logprocessor.anomaly.AnomalyEvent;
import com.aiincident.logprocessor.anomaly.AnomalyRepository;
import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.dependency.ServiceDependencyService;
import com.aiincident.logprocessor.dependency.ServiceDependencyType;
import com.aiincident.logprocessor.entity.ProcessedDeploymentEvent;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentEvidenceRepository;
import com.aiincident.logprocessor.incident.IncidentRepository;
import com.aiincident.logprocessor.repository.DeploymentEventRepository;
import com.aiincident.logprocessor.repository.LogEventRepository;
import com.aiincident.logprocessor.service.LogProcessorService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class RcaContextIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LogProcessorService logProcessorService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentEvidenceRepository evidenceRepository;

    @Autowired
    private AnomalyRepository anomalyRepository;

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private DeploymentEventRepository deploymentRepository;

    @Autowired
    private ServiceDependencyService dependencyService;

    @BeforeEach
    void setUp() {
        evidenceRepository.deleteAll();
        incidentRepository.deleteAll();
        anomalyRepository.deleteAll();
        logEventRepository.deleteAll();
        deploymentRepository.deleteAll();

        dependencyService.ensureDependency("order-service", "payment-service", ServiceDependencyType.HTTP_REST, "calls payment");
    }

    @Test
    @DisplayName("End-to-End: Retrieve full RCA context evidence package via GET /api/incidents/{id}/rca-context")
    void testGetRcaContextEndpoint() throws Exception {
        Instant base = Instant.parse("2026-08-23T12:00:00Z");

        // 1. Ingest deployment event (T - 2 min)
        ProcessedDeploymentEvent dep = new ProcessedDeploymentEvent(
                "dep-rca-1", "DEPLOYMENT_COMPLETED", "payment-service", "v2.5.0",
                base.minusSeconds(120), "tr-init", "{}", base.minusSeconds(120)
        );
        deploymentRepository.save(dep);

        // 2. Ingest anomaly event (T - 1 min)
        AnomalyEvent anomaly = new AnomalyEvent(
                "errorRate", "payment-service", 0.35, 0.01, 0.005, 0.05,
                base.minusSeconds(60), AnomalySeverity.CRITICAL,
                base.minusSeconds(60), base, "Error rate 35% breached critical threshold"
        );
        anomalyRepository.save(anomaly);

        // 3. Ingest operational failure logs to trigger incident correlation
        LogEvent evPay = new LogEvent(
                "ev-rca-pay", base, "payment-service", "ERROR", "DB_TIMEOUT", "tr-rca-1",
                "Payment failed: Database connection pool exhausted", null
        );
        LogEvent evOrd = new LogEvent(
                "ev-rca-ord", base.plusSeconds(10), "order-service", "ERROR", "SERVICE_UNAVAILABLE", "tr-rca-1",
                "Order failed: payment-service returned 503", null
        );
        LogEvent evUnrelated = new LogEvent(
                "ev-rca-unrelated", base.plusSeconds(15), "email-service", "INFO", "EMAIL_DISPATCHED", "tr-rca-9",
                "Email confirmation dispatched", null
        );

        logProcessorService.processEvent(evPay);
        logProcessorService.processEvent(evOrd);
        logProcessorService.processEvent(evUnrelated);

        // Verify incident was created
        Incident created = incidentRepository.findAll().stream().findFirst().orElseThrow();

        // 4. Query RCA Context via REST API
        mockMvc.perform(get("/api/incidents/" + created.getId() + "/rca-context")
                        .param("bufferMinutes", "5")
                        .param("maxLogs", "20")
                        .param("historicalTopK", "3")
                        .param("runbookTopK", "3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Summary verification
                .andExpect(jsonPath("$.summary", notNullValue()))
                .andExpect(jsonPath("$.summary.id", is(created.getId().intValue())))
                .andExpect(jsonPath("$.summary.primaryService", is("payment-service")))
                .andExpect(jsonPath("$.summary.synthesizedSummary", notNullValue()))
                // Timeline verification
                .andExpect(jsonPath("$.timeline", notNullValue()))
                .andExpect(jsonPath("$.timeline.totalEvents", greaterThanOrEqualTo(2)))
                // Relevant Logs verification (must include payment and order, exclude email-service)
                .andExpect(jsonPath("$.relevantLogs", notNullValue()))
                .andExpect(jsonPath("$.relevantLogs.length()", is(2)))
                .andExpect(jsonPath("$.relevantLogs[*].service", hasItem("payment-service")))
                .andExpect(jsonPath("$.relevantLogs[*].service", hasItem("order-service")))
                // Metrics verification
                .andExpect(jsonPath("$.metrics", notNullValue()))
                .andExpect(jsonPath("$.metrics.length()", greaterThanOrEqualTo(1)))
                // Dependencies verification
                .andExpect(jsonPath("$.dependencies", notNullValue()))
                .andExpect(jsonPath("$.dependencies.serviceTopologies", notNullValue()))
                // Primary failure verification
                .andExpect(jsonPath("$.primaryFailure", notNullValue()))
                .andExpect(jsonPath("$.primaryFailure.primaryCandidate.service", is("payment-service")))
                .andExpect(jsonPath("$.primaryFailure.primaryCandidate.isPrimary", is(true)))
                // Historical Incidents & Runbooks verification
                .andExpect(jsonPath("$.similarHistoricalIncidents", notNullValue()))
                .andExpect(jsonPath("$.relevantRunbooks", notNullValue()))
                // Metadata verification
                .andExpect(jsonPath("$.metadata", notNullValue()))
                .andExpect(jsonPath("$.metadata.totalLogsConsidered", is(3)))
                .andExpect(jsonPath("$.metadata.relevantLogsIncluded", is(2)));
    }

    @Test
    @DisplayName("Should return 404 for non-existent incident")
    void testGetRcaContextNotFound() throws Exception {
        mockMvc.perform(get("/api/incidents/999999/rca-context")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
