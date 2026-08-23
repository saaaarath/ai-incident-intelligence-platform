package com.aiincident.logprocessor.rca;

import com.aiincident.logprocessor.anomaly.AnomalyRepository;
import com.aiincident.logprocessor.dependency.ServiceDependencyService;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentEvidenceRepository;
import com.aiincident.logprocessor.incident.IncidentRepository;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class PrimaryFailureIntegrationTest {

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
    private ServiceDependencyService dependencyService;

    @BeforeEach
    void setUp() {
        evidenceRepository.deleteAll();
        incidentRepository.deleteAll();
        anomalyRepository.deleteAll();
        logEventRepository.deleteAll();
        dependencyService.initDefaultDependencies();
    }

    @Test
    @DisplayName("End-to-End: Primary Failure RCA distinguishing upstream failure from downstream symptoms via REST")
    void testCascadingRcaRestEndpoints() throws Exception {
        Instant t0 = Instant.parse("2026-08-23T12:00:00Z");
        Instant t1 = t0.plusSeconds(2);
        Instant t2 = t0.plusSeconds(4);

        // 1. Ingest cascading failure chain:
        // payment-service (DB timeout) -> order-service (payment unavailable)
        com.aiincident.logging.LogEvent evPay1 = new com.aiincident.logging.LogEvent(
                "ev-p1", t0, "payment-service", "ERROR", "DB_TIMEOUT", "tr-casc-1",
                "Payment failed: connection timeout to PostgreSQL database after 3000ms", null
        );
        com.aiincident.logging.LogEvent evPay2 = new com.aiincident.logging.LogEvent(
                "ev-p2", t1, "payment-service", "ERROR", "POOL_EXHAUSTED", "tr-casc-1",
                "HikariPool-1 connection pool exhausted: 100/100 active", null
        );
        com.aiincident.logging.LogEvent evOrd = new com.aiincident.logging.LogEvent(
                "ev-o1", t2, "order-service", "ERROR", "SERVICE_UNAVAILABLE", "tr-casc-1",
                "Order creation failed: downstream payment-service returned 503 Service Unavailable", null
        );

        logProcessorService.processEvent(evPay1);
        logProcessorService.processEvent(evPay2);
        logProcessorService.processEvent(evOrd);

        // 2. Query correlated incident
        Incident incident = incidentRepository.findAll().getFirst();

        // 3. Test GET /incidents/{id}/primary-failure endpoint
        mockMvc.perform(get("/incidents/" + incident.getId() + "/primary-failure")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId", is(incident.getId().intValue())))
                .andExpect(jsonPath("$.primaryCandidate.service", is("payment-service")))
                .andExpect(jsonPath("$.primaryCandidate.isPrimary", is(true)))
                .andExpect(jsonPath("$.primaryCandidate.isSymptom", is(false)))
                .andExpect(jsonPath("$.primaryCandidate.symptomServices", hasItem("order-service")))
                .andExpect(jsonPath("$.symptoms", hasSize(1)))
                .andExpect(jsonPath("$.symptoms[0].service", is("order-service")))
                .andExpect(jsonPath("$.symptoms[0].isSymptom", is(true)))
                .andExpect(jsonPath("$.summary", notNullValue()));

        // 4. Test POST /api/incidents/analyze-primary-failure endpoint with raw evidence
        String evidenceJson = String.format("""
                [
                    {
                        "service": "postgres",
                        "eventType": "DB_FAILURE",
                        "severity": "CRITICAL",
                        "message": "PostgreSQL connection pool exhausted",
                        "timestamp": "%s"
                    },
                    {
                        "service": "payment-service",
                        "eventType": "DB_TIMEOUT",
                        "severity": "HIGH",
                        "message": "Payment query timeout",
                        "timestamp": "%s"
                    },
                    {
                        "service": "order-service",
                        "eventType": "ORDER_TIMEOUT",
                        "severity": "HIGH",
                        "message": "Order request timeout",
                        "timestamp": "%s"
                    }
                ]
                """, t0, t1, t2);

        mockMvc.perform(post("/api/incidents/analyze-primary-failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidenceJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryCandidate.service", is("postgres")))
                .andExpect(jsonPath("$.primaryCandidate.isPrimary", is(true)))
                .andExpect(jsonPath("$.primaryCandidate.symptomServices", hasItem("payment-service")))
                .andExpect(jsonPath("$.primaryCandidate.symptomServices", hasItem("order-service")));
    }
}
