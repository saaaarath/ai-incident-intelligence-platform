package com.aiincident.logprocessor.timeline;

import com.aiincident.logprocessor.anomaly.AnomalyEvent;
import com.aiincident.logprocessor.anomaly.AnomalyRepository;
import com.aiincident.logprocessor.anomaly.AnomalySeverity;
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
class IncidentTimelineIntegrationTest {

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

    @BeforeEach
    void setUp() {
        evidenceRepository.deleteAll();
        incidentRepository.deleteAll();
        anomalyRepository.deleteAll();
        logEventRepository.deleteAll();
        deploymentRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End: Retrieve chronological incident timeline combining anomalies, logs, deployments, failures, and metrics via REST")
    void testGetIncidentTimelineEndpoint() throws Exception {
        Instant base = Instant.parse("2026-08-23T12:00:00Z");

        // 1. Ingest deployment event (T - 2 min)
        ProcessedDeploymentEvent dep = new ProcessedDeploymentEvent(
                "dep-e2e-1", "DEPLOYMENT_COMPLETED", "payment-service", "v3.0.0",
                base.minusSeconds(120), "tr-init", "{}", base.minusSeconds(120)
        );
        deploymentRepository.save(dep);

        // 2. Ingest anomaly event (T - 1 min)
        AnomalyEvent anomaly = new AnomalyEvent(
                "errorRate", "payment-service", 0.20, 0.01, 0.005, 0.05,
                base.minusSeconds(60), AnomalySeverity.HIGH,
                base.minusSeconds(60), base, "Error rate 20% breached threshold"
        );
        anomalyRepository.save(anomaly);

        // 3. Ingest operational failure logs to trigger incident correlation (T and T + 10s)
        com.aiincident.logging.LogEvent evPay = new com.aiincident.logging.LogEvent(
                "ev-e2e-pay", base, "payment-service", "ERROR", "DB_TIMEOUT", "tr-e2e",
                "Payment failed: DB connection timeout after 3000ms", null
        );
        com.aiincident.logging.LogEvent evOrd = new com.aiincident.logging.LogEvent(
                "ev-e2e-ord", base.plusSeconds(10), "order-service", "ERROR", "SERVICE_UNAVAILABLE", "tr-e2e",
                "Order failed: payment-service unavailable", null
        );

        logProcessorService.processEvent(evPay);
        logProcessorService.processEvent(evOrd);

        // 4. Ingest an informational log event (T + 20s)
        com.aiincident.logging.LogEvent evInfo = new com.aiincident.logging.LogEvent(
                "ev-e2e-info", base.plusSeconds(20), "order-service", "WARN", "CIRCUIT_BREAKER_OPEN", "tr-e2e",
                "Circuit breaker opened for payment-service", null
        );
        logProcessorService.processEvent(evInfo);

        // Retrieve created incident
        Incident incident = incidentRepository.findAll().getFirst();

        // 5. Test GET /incidents/{id}/timeline
        mockMvc.perform(get("/incidents/" + incident.getId() + "/timeline")
                        .param("bufferMinutes", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId", is(incident.getId().intValue())))
                .andExpect(jsonPath("$.primaryService", is("payment-service")))
                .andExpect(jsonPath("$.totalEvents", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.events[0].type", is("DEPLOYMENT")))
                .andExpect(jsonPath("$.events[0].service", is("payment-service")))
                .andExpect(jsonPath("$.events[1].type", is("ANOMALY")))
                .andExpect(jsonPath("$.events[1].service", is("payment-service")))
                .andExpect(jsonPath("$.events[*].type", hasItem("SERVICE_FAILURE")))
                .andExpect(jsonPath("$.events[*].type", hasItem("LOG")))
                .andExpect(jsonPath("$.summary", notNullValue()));
    }
}
