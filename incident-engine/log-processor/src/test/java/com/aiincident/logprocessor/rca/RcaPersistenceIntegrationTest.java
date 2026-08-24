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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class RcaPersistenceIntegrationTest {

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

    @Autowired
    private RcaReportRepository rcaReportRepository;

    private Long incidentNumericId;
    private String incidentStringId;

    @BeforeEach
    void setUp() {
        rcaReportRepository.deleteAll();
        evidenceRepository.deleteAll();
        incidentRepository.deleteAll();
        anomalyRepository.deleteAll();
        logEventRepository.deleteAll();
        deploymentRepository.deleteAll();

        dependencyService.ensureDependency("order-service", "payment-service", ServiceDependencyType.HTTP_REST, "calls payment");

        Instant base = Instant.parse("2026-08-23T12:00:00Z");

        // 1. Ingest deployment event
        ProcessedDeploymentEvent dep = new ProcessedDeploymentEvent(
                "dep-persist-1", "DEPLOYMENT_COMPLETED", "payment-service", "v2.5.0",
                base.minusSeconds(120), "tr-init", "{}", base.minusSeconds(120)
        );
        deploymentRepository.save(dep);

        // 2. Ingest anomaly event
        AnomalyEvent anomaly = new AnomalyEvent(
                "errorRate", "payment-service", 0.40, 0.01, 0.005, 0.05,
                base.minusSeconds(60), AnomalySeverity.CRITICAL,
                base.minusSeconds(60), base, "Error rate 40% breached critical threshold"
        );
        anomalyRepository.save(anomaly);

        // 3. Ingest operational failure logs
        LogEvent evPay = new LogEvent(
                "ev-pers-pay", base, "payment-service", "ERROR", "DB_TIMEOUT", "tr-pers-1",
                "Payment failed: HikariPool-1 database connection pool exhausted timeout after 3000ms", null
        );
        LogEvent evOrd = new LogEvent(
                "ev-pers-ord", base.plusSeconds(10), "order-service", "ERROR", "SERVICE_UNAVAILABLE", "tr-pers-1",
                "Order failed: payment-service returned 503 Service Unavailable", null
        );

        logProcessorService.processEvent(evPay);
        logProcessorService.processEvent(evOrd);

        Incident created = incidentRepository.findAll().stream().findFirst().orElseThrow();
        incidentNumericId = created.getId();
        incidentStringId = created.getIncidentId();
    }

    @Test
    @DisplayName("End-to-End: POST /api/incidents/{id}/analyze generates and persists RCA report")
    void testAnalyzeIncident_PersistsReport() throws Exception {
        mockMvc.perform(post("/api/incidents/" + incidentNumericId + "/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause.rootService", is("payment-service")))
                .andExpect(jsonPath("$.confidence.level", notNullValue()))
                .andExpect(jsonPath("$.validation.valid", is(true)));

        // Verify report is persisted in database
        org.assertj.core.api.Assertions.assertThat(rcaReportRepository.count()).isEqualTo(1);
        var persisted = rcaReportRepository.findAll().get(0);
        org.assertj.core.api.Assertions.assertThat(persisted.getRootService()).isEqualTo("payment-service");
        org.assertj.core.api.Assertions.assertThat(persisted.getConfidenceLevel()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("GET /api/incidents/{id}/analysis retrieves previously persisted report")
    void testGetIncidentAnalysis_RetrievesPersistedReport() throws Exception {
        // Trigger initial analysis
        mockMvc.perform(post("/api/incidents/" + incidentNumericId + "/analyze"))
                .andExpect(status().isOk());

        // Retrieve persisted analysis
        mockMvc.perform(get("/api/incidents/" + incidentNumericId + "/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause.rootService", is("payment-service")))
                .andExpect(jsonPath("$.metadata.incidentIdentifier", notNullValue()));
    }

    @Test
    @DisplayName("Duplicate Prevention: POST /api/incidents/{id}/analyze returns existing report without forceReanalyze")
    void testAnalyzeIncident_DuplicatePrevention() throws Exception {
        // Initial analysis
        mockMvc.perform(post("/api/incidents/" + incidentNumericId + "/analyze"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(rcaReportRepository.count()).isEqualTo(1);

        // Second analysis with forceReanalyze=false (default) -> should not create a second row
        mockMvc.perform(post("/api/incidents/" + incidentNumericId + "/analyze?forceReanalyze=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause.rootService", is("payment-service")));

        org.assertj.core.api.Assertions.assertThat(rcaReportRepository.count()).isEqualTo(1);

        // Third analysis with forceReanalyze=true -> should create a new analysis entry
        mockMvc.perform(post("/api/incidents/" + incidentNumericId + "/analyze?forceReanalyze=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause.rootService", is("payment-service")));

        org.assertj.core.api.Assertions.assertThat(rcaReportRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("GET /api/incidents/999999/analysis returns 404 for unanalyzed/unknown incident")
    void testGetIncidentAnalysis_NotFound() throws Exception {
        mockMvc.perform(get("/api/incidents/999999/analysis"))
                .andExpect(status().isNotFound());
    }
}
