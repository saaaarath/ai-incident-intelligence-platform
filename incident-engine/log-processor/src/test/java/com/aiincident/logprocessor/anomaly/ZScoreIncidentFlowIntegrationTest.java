package com.aiincident.logprocessor.anomaly;

import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.incident.IncidentRepository;
import com.aiincident.logprocessor.incident.IncidentStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class ZScoreIncidentFlowIntegrationTest {

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
    @DisplayName("Z-Score Detection to Incident Flow: Z-score anomaly automatically creates an Incident in Assignment 4.4")
    void testZScoreAnomalyToIncidentFlow() throws Exception {
        Instant baseTime = Instant.parse("2026-08-23T12:00:00Z");

        // 1. Seed baseline data (15 minutes with steady 2% errors in payment-service)
        List<ProcessedLogEvent> logs = new ArrayList<>();
        for (int min = 0; min < 15; min++) {
            for (int req = 0; req < 50; req++) {
                Instant t = baseTime.plusSeconds(min * 60L + req);
                boolean isError = (req == 0); // 1 error per 50 requests = 2% error rate
                logs.add(new ProcessedLogEvent(
                        "z-base-" + min + "-" + req,
                        t,
                        "payment-service",
                        isError ? "ERROR" : "INFO",
                        isError ? "DB_TIMEOUT" : "PAYMENT_CREATED",
                        "trace-z-base-" + min + "-" + req,
                        "Payment operation",
                        "{\"latencyMs\": 40}",
                        t
                ));
            }
        }
        logEventRepository.saveAll(logs);

        // 2. Seed anomalous current window (12:15 to 12:16) with 50% error spike (25 errors out of 50 requests)
        List<ProcessedLogEvent> currentLogs = new ArrayList<>();
        for (int req = 0; req < 50; req++) {
            Instant t = baseTime.plusSeconds(15 * 60L + req);
            boolean isError = req < 25; // 25 errors = 50% error rate
            currentLogs.add(new ProcessedLogEvent(
                    "z-curr-" + req,
                    t,
                    "payment-service",
                    isError ? "ERROR" : "INFO",
                    isError ? "SERVICE_UNAVAILABLE" : "PAYMENT_CREATED",
                    "trace-z-curr-" + req,
                    "Payment spike operation",
                    "{\"latencyMs\": 45}",
                    t
            ));
        }
        logEventRepository.saveAll(currentLogs);

        // 3. Trigger anomaly detection using strategy=ZSCORE
        mockMvc.perform(post("/api/anomalies/detect")
                        .param("service", "payment-service")
                        .param("strategy", "ZSCORE")
                        .param("currentStart", "2026-08-23T12:15:00Z")
                        .param("currentEnd", "2026-08-23T12:16:00Z")
                        .param("baselineStart", "2026-08-23T12:00:00Z")
                        .param("baselineEnd", "2026-08-23T12:14:59Z")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].service", is("payment-service")))
                .andExpect(jsonPath("$[0].metric", is("errorRate")))
                .andExpect(jsonPath("$[0].currentValue", is(0.50)));

        // 4. Trigger detectAndSaveAnomalies across all services
        mockMvc.perform(post("/api/anomalies/detect")
                        .param("currentStart", "2026-08-23T12:15:00Z")
                        .param("currentEnd", "2026-08-23T12:16:00Z")
                        .param("baselineStart", "2026-08-23T12:00:00Z")
                        .param("baselineEnd", "2026-08-23T12:14:59Z")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 5. Verify that an Incident was automatically created in the existing 4.4 Incident system
        mockMvc.perform(get("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].primaryService", is("payment-service")))
                .andExpect(jsonPath("$[0].status", is("OPEN")))
                .andExpect(jsonPath("$[0].severity", is("CRITICAL")));
    }
}
