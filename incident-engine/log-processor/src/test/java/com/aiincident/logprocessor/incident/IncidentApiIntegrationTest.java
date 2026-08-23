package com.aiincident.logprocessor.incident;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
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
class IncidentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IncidentRepository incidentRepository;

    @BeforeEach
    void setUp() {
        incidentRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /incidents: Should query and filter incidents by status, severity, service, and time range")
    void testGetIncidentsWithFilters() throws Exception {
        Instant t1 = Instant.parse("2026-08-23T10:00:00Z");
        Instant t2 = Instant.parse("2026-08-23T11:00:00Z");
        Instant t3 = Instant.parse("2026-08-23T12:00:00Z");

        Incident inc1 = incidentRepository.save(new Incident("High Error on order", AnomalySeverity.CRITICAL, IncidentStatus.OPEN, "order-service", t1, t1, "d", "errorRate"));
        Incident inc2 = incidentRepository.save(new Incident("Latency on payment", AnomalySeverity.HIGH, IncidentStatus.INVESTIGATING, "payment-service", t2, t2, "d", "latencyAvg"));
        Incident inc3 = incidentRepository.save(new Incident("Error on inventory", AnomalySeverity.MEDIUM, IncidentStatus.RESOLVED, "inventory-service", t3, t3, "d", "errorRate"));

        // 1. Unfiltered GET /incidents
        mockMvc.perform(get("/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // 2. Filter by status: ?status=OPEN
        mockMvc.perform(get("/incidents").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].primaryService", is("order-service")))
                .andExpect(jsonPath("$[0].status", is("OPEN")));

        // 3. Filter by severity: ?severity=HIGH
        mockMvc.perform(get("/incidents").param("severity", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].primaryService", is("payment-service")))
                .andExpect(jsonPath("$[0].severity", is("HIGH")));

        // 4. Filter by service: ?service=inventory-service
        mockMvc.perform(get("/incidents").param("service", "inventory-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].primaryService", is("inventory-service")));

        // 5. Filter by time range: from 10:30 to 11:30
        mockMvc.perform(get("/incidents")
                        .param("from", "2026-08-23T10:30:00Z")
                        .param("to", "2026-08-23T11:30:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].primaryService", is("payment-service")));

        // 6. Test compatibility via /api/incidents prefix
        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("GET /incidents/{id}: Should return incident when found or 404 when not found")
    void testGetIncidentById() throws Exception {
        Incident incident = incidentRepository.save(new Incident("Test incident", AnomalySeverity.HIGH, IncidentStatus.OPEN, "order-service", Instant.now(), Instant.now(), "desc", "errorRate"));

        mockMvc.perform(get("/incidents/" + incident.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(incident.getId().intValue())))
                .andExpect(jsonPath("$.title", is("Test incident")))
                .andExpect(jsonPath("$.primaryService", is("order-service")));

        mockMvc.perform(get("/incidents/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /incidents/{id}/acknowledge, resolve, close lifecycle management")
    void testIncidentLifecycleEndpoints() throws Exception {
        Incident incident = incidentRepository.save(new Incident("Lifecycle Incident", AnomalySeverity.CRITICAL, IncidentStatus.OPEN, "order-service", Instant.now(), Instant.now(), "desc", "errorRate"));
        Long id = incident.getId();

        // 1. POST /incidents/{id}/acknowledge -> moves from OPEN to INVESTIGATING
        mockMvc.perform(post("/incidents/" + id + "/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INVESTIGATING")));

        // Attempting to acknowledge again should return 400 Bad Request
        mockMvc.perform(post("/incidents/" + id + "/acknowledge"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", notNullValue()));

        // 2. POST /incidents/{id}/resolve -> moves to RESOLVED and sets resolvedAt
        mockMvc.perform(post("/incidents/" + id + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESOLVED")))
                .andExpect(jsonPath("$.resolvedAt", notNullValue()));

        // 3. POST /incidents/{id}/close -> moves to CLOSED
        mockMvc.perform(post("/incidents/" + id + "/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CLOSED")))
                .andExpect(jsonPath("$.resolvedAt", notNullValue()));

        // Resolving a closed incident should return 400 Bad Request
        mockMvc.perform(post("/incidents/" + id + "/resolve"))
                .andExpect(status().isBadRequest());

        // Calling action on nonexistent incident returns 404 Not Found
        mockMvc.perform(post("/incidents/99999/acknowledge"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/incidents/99999/resolve"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/incidents/99999/close"))
                .andExpect(status().isNotFound());
    }
}
