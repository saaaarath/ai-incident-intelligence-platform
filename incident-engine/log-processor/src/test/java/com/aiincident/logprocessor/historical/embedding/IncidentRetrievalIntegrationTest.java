package com.aiincident.logprocessor.historical.embedding;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentEvidence;
import com.aiincident.logprocessor.incident.IncidentEvidenceRepository;
import com.aiincident.logprocessor.incident.IncidentRepository;
import com.aiincident.logprocessor.incident.IncidentStatus;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentRetrievalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentEvidenceRepository evidenceRepository;

    private Incident testIncident;

    @BeforeEach
    void setUp() {
        testIncident = new Incident(
                "Payment Service Database Connection Timeout",
                AnomalySeverity.CRITICAL,
                IncidentStatus.OPEN,
                "payment-service",
                Instant.now(),
                Instant.now(),
                "HikariPool-1 connections exhausted, database queries timing out",
                "database.connection.timeout"
        );
        testIncident.setRootService("payment-service");
        testIncident.setAffectedServices(Set.of("order-service", "payment-service"));
        testIncident = incidentRepository.save(testIncident);

        IncidentEvidence ev1 = new IncidentEvidence(
                testIncident.getId(),
                "ev-test-1",
                Instant.now(),
                "payment-service",
                "DB_TIMEOUT",
                AnomalySeverity.CRITICAL,
                "Database connection pool timeout: no connections available in pool",
                "tr-test-1",
                "{}"
        );
        evidenceRepository.save(ev1);
    }

    @Test
    @DisplayName("REST API: GET /api/incidents/{id}/similar retrieves historically similar incidents for current incident")
    void testGetSimilarIncidentsEndpoint() throws Exception {
        mockMvc.perform(get("/api/incidents/{id}/similar", testIncident.getId())
                        .param("topK", "3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].type").value("HISTORICAL_INCIDENT"))
                .andExpect(jsonPath("$[0].similarityScore").value(greaterThan(0.1)))
                .andExpect(jsonPath("$[0].documentId").exists());
    }

    @Test
    @DisplayName("REST API: GET /api/incidents/{id}/runbooks retrieves relevant operational runbooks for current incident")
    void testGetRelevantRunbooksEndpoint() throws Exception {
        mockMvc.perform(get("/api/incidents/{id}/runbooks", testIncident.getId())
                        .param("topK", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("RUNBOOK"))
                .andExpect(jsonPath("$[0].documentId").value("RB:RB-DB-001"))
                .andExpect(jsonPath("$[0].similarityScore").value(greaterThan(0.1)));
    }

    @Test
    @DisplayName("REST API: GET /api/incidents/{id}/context retrieves combined knowledge context for current incident")
    void testGetIncidentContextEndpoint() throws Exception {
        mockMvc.perform(get("/api/incidents/{id}/context", testIncident.getId())
                        .param("topK", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").exists())
                .andExpect(jsonPath("$.primaryService").value("payment-service"))
                .andExpect(jsonPath("$.synthesizedSummary").exists())
                .andExpect(jsonPath("$.similarIncidents", hasSize(2)))
                .andExpect(jsonPath("$.relevantRunbooks", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("REST API: GET /api/incidents/99999/similar returns 404 for unknown incident")
    void testUnknownIncidentReturns404() throws Exception {
        mockMvc.perform(get("/api/incidents/99999/similar"))
                .andExpect(status().isNotFound());
    }
}
