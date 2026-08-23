package com.aiincident.logprocessor.anomaly;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class AnomalyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private AnomalyRepository anomalyRepository;

    @BeforeEach
    void setUp() {
        anomalyRepository.deleteAll();
        logEventRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End: Should detect controlled error spike and not detect anomalies during normal behavior")
    void testEndToEndAnomalyDetection() throws Exception {
        Instant baseTime = Instant.parse("2026-08-23T12:00:00Z");

        // 1. Seed baseline period (12:00:00 to 12:15:00) with normal events:
        // - order-service: 10 events per minute, 0 errors, ~50ms latency
        // - inventory-service: 5 events per minute, 0 errors, ~30ms latency
        List<ProcessedLogEvent> baselineLogs = new ArrayList<>();
        for (int minute = 0; minute < 15; minute++) {
            for (int req = 0; req < 10; req++) {
                Instant t = baseTime.plusSeconds(minute * 60L + req * 5L);
                baselineLogs.add(new ProcessedLogEvent(
                        "order-base-" + minute + "-" + req,
                        t,
                        "order-service",
                        "INFO",
                        "ORDER_CREATED",
                        "trace-base-" + minute + "-" + req,
                        "Order processed normally",
                        "{\"latencyMs\": 50}",
                        t
                ));
            }
            for (int req = 0; req < 5; req++) {
                Instant t = baseTime.plusSeconds(minute * 60L + req * 10L);
                baselineLogs.add(new ProcessedLogEvent(
                        "inv-base-" + minute + "-" + req,
                        t,
                        "inventory-service",
                        "INFO",
                        "INVENTORY_RESERVED",
                        "trace-inv-base-" + minute + "-" + req,
                        "Inventory reserved normally",
                        "{\"latencyMs\": 30}",
                        t
                ));
            }
        }
        logEventRepository.saveAll(baselineLogs);

        // 2. Current window (12:15:00 to 12:16:00):
        // - order-service suffers controlled ERROR SPIKE (8 errors out of 10 events = 80% error rate)
        // - inventory-service remains NORMAL (5 events, 0 errors)
        List<ProcessedLogEvent> currentLogs = new ArrayList<>();
        for (int req = 0; req < 10; req++) {
            Instant t = baseTime.plusSeconds(15 * 60L + req * 5L);
            boolean isError = req >= 2; // 8 errors
            currentLogs.add(new ProcessedLogEvent(
                    "order-curr-" + req,
                    t,
                    "order-service",
                    isError ? "ERROR" : "INFO",
                    isError ? "PAYMENT_FAILED" : "ORDER_CREATED",
                    "trace-curr-" + req,
                    isError ? "Injected failure occurred" : "Order processed",
                    "{\"latencyMs\": 60}",
                    t
            ));
        }
        for (int req = 0; req < 5; req++) {
            Instant t = baseTime.plusSeconds(15 * 60L + req * 10L);
            currentLogs.add(new ProcessedLogEvent(
                    "inv-curr-" + req,
                    t,
                    "inventory-service",
                    "INFO",
                    "INVENTORY_RESERVED",
                    "trace-inv-curr-" + req,
                    "Inventory reserved normally",
                    "{\"latencyMs\": 30}",
                    t
            ));
        }
        logEventRepository.saveAll(currentLogs);

        // 3. Trigger anomaly detection for the entire system over current window vs baseline
        mockMvc.perform(post("/api/anomalies/detect")
                        .param("currentStart", "2026-08-23T12:15:00Z")
                        .param("currentEnd", "2026-08-23T12:16:00Z")
                        .param("baselineStart", "2026-08-23T12:00:00Z")
                        .param("baselineEnd", "2026-08-23T12:15:00Z")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].service", is("order-service")))
                .andExpect(jsonPath("$[0].metric", is("errorRate")))
                .andExpect(jsonPath("$[0].currentValue", is(0.8)))
                .andExpect(jsonPath("$[0].baselineMean", is(0.0)))
                .andExpect(jsonPath("$[0].severity", is("CRITICAL")));

        // 4. Query persisted anomalies via GET endpoint
        mockMvc.perform(get("/api/anomalies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].service", is("order-service")))
                .andExpect(jsonPath("$[0].metric", is("errorRate")));

        // 5. Query anomalies filtered by service "inventory-service" -> expect 0 anomalies
        mockMvc.perform(get("/api/anomalies")
                        .param("service", "inventory-service")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
