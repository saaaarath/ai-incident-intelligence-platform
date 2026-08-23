package com.aiincident.logprocessor.incident;

import com.aiincident.logprocessor.anomaly.AnomalyRepository;
import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.repository.LogEventRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class IncidentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private AnomalyRepository anomalyRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @BeforeEach
    void setUp() {
        incidentRepository.deleteAll();
        anomalyRepository.deleteAll();
        logEventRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End: Injected failure automatically produces an Incident with duplicate prevention and lifecycle transitions")
    void testEndToEndIncidentCreationAndLifecycle() throws Exception {
        Instant baseTime = Instant.parse("2026-08-23T12:00:00Z");

        // 1. Seed baseline logs (15 minutes of normal traffic for order-service)
        List<ProcessedLogEvent> baselineLogs = new ArrayList<>();
        for (int minute = 0; minute < 15; minute++) {
            for (int req = 0; req < 10; req++) {
                Instant t = baseTime.plusSeconds(minute * 60L + req * 5L);
                baselineLogs.add(new ProcessedLogEvent(
                        "base-log-" + minute + "-" + req,
                        t,
                        "order-service",
                        "INFO",
                        "ORDER_CREATED",
                        "trace-base-" + minute + "-" + req,
                        "Order normal",
                        "{\"latencyMs\": 45}",
                        t
                ));
            }
        }
        logEventRepository.saveAll(baselineLogs);

        // 2. Injected failure in current window: 8 ERROR logs out of 10 requests (80% error spike)
        List<ProcessedLogEvent> failureLogs = new ArrayList<>();
        for (int req = 0; req < 10; req++) {
            Instant t = baseTime.plusSeconds(15 * 60L + req * 5L);
            boolean isError = req >= 2;
            failureLogs.add(new ProcessedLogEvent(
                    "failure-log-" + req,
                    t,
                    "order-service",
                    isError ? "ERROR" : "INFO",
                    isError ? "SERVICE_UNAVAILABLE" : "ORDER_CREATED",
                    "trace-fail-" + req,
                    isError ? "Injected failure: payment-service unavailable" : "Order normal",
                    "{\"latencyMs\": 50}",
                    t
            ));
        }
        logEventRepository.saveAll(failureLogs);

        // 3. Trigger anomaly detection -> should automatically detect anomaly AND create an OPEN Incident
        mockMvc.perform(post("/api/anomalies/detect")
                        .param("currentStart", "2026-08-23T12:15:00Z")
                        .param("currentEnd", "2026-08-23T12:16:00Z")
                        .param("baselineStart", "2026-08-23T12:00:00Z")
                        .param("baselineEnd", "2026-08-23T12:15:00Z")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // 4. Verify Incident was created in DB and accessible via REST API
        mockMvc.perform(get("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].primaryService", is("order-service")))
                .andExpect(jsonPath("$[0].title", is("High Error Rate on order-service")))
                .andExpect(jsonPath("$[0].severity", is("CRITICAL")))
                .andExpect(jsonPath("$[0].status", is("OPEN")))
                .andExpect(jsonPath("$[0].startedAt", is("2026-08-23T12:15:00Z")))
                .andExpect(jsonPath("$[0].resolvedAt", nullValue()));

        Long incidentId = incidentRepository.findAll().getFirst().getId();

        // 5. Trigger anomaly detection again for subsequent minute (active failure window) -> Duplicate Prevention
        mockMvc.perform(post("/api/anomalies/detect")
                        .param("currentStart", "2026-08-23T12:15:00Z")
                        .param("currentEnd", "2026-08-23T12:16:00Z")
                        .param("baselineStart", "2026-08-23T12:00:00Z")
                        .param("baselineEnd", "2026-08-23T12:15:00Z")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Verify still exactly 1 Incident
        mockMvc.perform(get("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // 6. Test Lifecycle Transitions:
        // Transition to INVESTIGATING
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .param("status", "INVESTIGATING")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INVESTIGATING")))
                .andExpect(jsonPath("$.resolvedAt", nullValue()));

        // Transition to RESOLVED
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .param("status", "RESOLVED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESOLVED")))
                .andExpect(jsonPath("$.resolvedAt", notNullValue()));

        // Transition to CLOSED
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .param("status", "CLOSED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CLOSED")))
                .andExpect(jsonPath("$.resolvedAt", notNullValue()));

        // 7. Verify filtering by status
        mockMvc.perform(get("/api/incidents")
                        .param("status", "CLOSED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/incidents")
                        .param("status", "OPEN")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
