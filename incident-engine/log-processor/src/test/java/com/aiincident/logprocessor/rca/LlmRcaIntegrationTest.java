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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class LlmRcaIntegrationTest {

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
    @DisplayName("End-to-End: Execute AI RCA and produce structured RCA report via POST /api/incidents/{id}/rca")
    void testGenerateIncidentRcaEndpoint() throws Exception {
        Instant base = Instant.parse("2026-08-23T12:00:00Z");

        // 1. Ingest deployment event (T - 2 min)
        ProcessedDeploymentEvent dep = new ProcessedDeploymentEvent(
                "dep-llm-1", "DEPLOYMENT_COMPLETED", "payment-service", "v2.5.0",
                base.minusSeconds(120), "tr-init", "{}", base.minusSeconds(120)
        );
        deploymentRepository.save(dep);

        // 2. Ingest anomaly event (T - 1 min)
        AnomalyEvent anomaly = new AnomalyEvent(
                "errorRate", "payment-service", 0.40, 0.01, 0.005, 0.05,
                base.minusSeconds(60), AnomalySeverity.CRITICAL,
                base.minusSeconds(60), base, "Error rate 40% breached critical threshold"
        );
        anomalyRepository.save(anomaly);

        // 3. Ingest operational failure logs to trigger incident correlation
        LogEvent evPay = new LogEvent(
                "ev-llm-pay", base, "payment-service", "ERROR", "DB_TIMEOUT", "tr-llm-1",
                "Payment failed: HikariPool-1 database connection pool exhausted timeout after 3000ms", null
        );
        LogEvent evOrd = new LogEvent(
                "ev-llm-ord", base.plusSeconds(10), "order-service", "ERROR", "SERVICE_UNAVAILABLE", "tr-llm-1",
                "Order failed: payment-service returned 503 Service Unavailable", null
        );

        logProcessorService.processEvent(evPay);
        logProcessorService.processEvent(evOrd);

        Incident created = incidentRepository.findAll().stream().findFirst().orElseThrow();

        // 4. Trigger AI RCA via POST /api/incidents/{id}/rca
        mockMvc.perform(post("/api/incidents/" + created.getId() + "/rca")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Root Cause verification
                .andExpect(jsonPath("$.rootCause", notNullValue()))
                .andExpect(jsonPath("$.rootCause.rootService", is("payment-service")))
                .andExpect(jsonPath("$.rootCause.statement", notNullValue()))
                .andExpect(jsonPath("$.rootCause.inferenceDetails", notNullValue()))
                // Confidence verification
                .andExpect(jsonPath("$.confidence", notNullValue()))
                .andExpect(jsonPath("$.confidence.level", is("HIGH")))
                .andExpect(jsonPath("$.confidence.score", greaterThanOrEqualTo(0.8)))
                .andExpect(jsonPath("$.confidence.rationale", notNullValue()))
                // Evidence verification
                .andExpect(jsonPath("$.evidence", notNullValue()))
                .andExpect(jsonPath("$.evidence.length()", greaterThanOrEqualTo(2)))
                // Alternative Hypotheses verification
                .andExpect(jsonPath("$.alternativeHypotheses", notNullValue()))
                .andExpect(jsonPath("$.alternativeHypotheses.length()", greaterThanOrEqualTo(1)))
                // Affected Services verification
                .andExpect(jsonPath("$.affectedServices", notNullValue()))
                .andExpect(jsonPath("$.affectedServices.rootService", is("payment-service")))
                // Recommended Investigation verification
                .andExpect(jsonPath("$.recommendedInvestigation", notNullValue()))
                .andExpect(jsonPath("$.recommendedInvestigation.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recommendedInvestigation[0].action", notNullValue()))
                .andExpect(jsonPath("$.recommendedInvestigation[0].justification", notNullValue()))
                // Historical References verification
                .andExpect(jsonPath("$.historicalReferences", notNullValue()))
                // Metadata verification
                .andExpect(jsonPath("$.metadata", notNullValue()))
                .andExpect(jsonPath("$.metadata.provider", is("mock")));

        // 5. Also verify GET /api/incidents/{id}/rca
        mockMvc.perform(get("/api/incidents/" + created.getId() + "/rca")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause.rootService", is("payment-service")));
    }
}
