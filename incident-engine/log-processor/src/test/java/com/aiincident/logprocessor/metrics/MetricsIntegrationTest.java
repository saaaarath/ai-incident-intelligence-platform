package com.aiincident.logprocessor.metrics;

import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.repository.LogEventRepository;
import java.time.Instant;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class MetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LogEventRepository logEventRepository;

    @BeforeEach
    void setUp() {
        logEventRepository.deleteAll();
    }

    @Test
    @DisplayName("Should aggregate stored operational events via REST API")
    void testMetricsCalculationFromStoredEvents() throws Exception {
        Instant baseTime = Instant.parse("2026-08-23T11:00:00Z");

        // Window 1: 11:00:00 - 11:01:00 (Order Service - 2 events: 1 INFO with 50ms latency, 1 ERROR with 150ms latency)
        ProcessedLogEvent e1 = new ProcessedLogEvent(
                "evt-101",
                baseTime.plusSeconds(10),
                "order-service",
                "INFO",
                "ORDER_CREATED",
                "trace-1",
                "Order created",
                "{\"latencyMs\": 50}",
                baseTime.plusSeconds(10)
        );
        ProcessedLogEvent e2 = new ProcessedLogEvent(
                "evt-102",
                baseTime.plusSeconds(30),
                "order-service",
                "ERROR",
                "PAYMENT_FAILED",
                "trace-1",
                "Payment failed",
                "{\"latencyMs\": 150}",
                baseTime.plusSeconds(30)
        );

        // Window 2: 11:01:00 - 11:02:00 (Order Service - 1 event: 1 INFO with 100ms latency)
        ProcessedLogEvent e3 = new ProcessedLogEvent(
                "evt-103",
                baseTime.plusSeconds(70),
                "order-service",
                "INFO",
                "ORDER_CREATED",
                "trace-2",
                "Order created",
                "{\"latencyMs\": 100}",
                baseTime.plusSeconds(70)
        );

        // Payment Service: 1 event
        ProcessedLogEvent e4 = new ProcessedLogEvent(
                "evt-104",
                baseTime.plusSeconds(20),
                "payment-service",
                "INFO",
                "PAYMENT_CREATED",
                "trace-3",
                "Payment created",
                "{\"latencyMs\": 75}",
                baseTime.plusSeconds(20)
        );

        logEventRepository.saveAll(List.of(e1, e2, e3, e4));

        // 1. Query windowed metrics for order-service with 1-minute windows
        mockMvc.perform(get("/api/metrics")
                        .param("service", "order-service")
                        .param("from", "2026-08-23T11:00:00Z")
                        .param("to", "2026-08-23T11:05:00Z")
                        .param("windowMinutes", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                // Window 1
                .andExpect(jsonPath("$[0].service", is("order-service")))
                .andExpect(jsonPath("$[0].totalEvents", is(2)))
                .andExpect(jsonPath("$[0].errorCount", is(1)))
                .andExpect(jsonPath("$[0].errorRate", is(0.5)))
                .andExpect(jsonPath("$[0].latency.count", is(2)))
                .andExpect(jsonPath("$[0].latency.min", is(50.0)))
                .andExpect(jsonPath("$[0].latency.max", is(150.0)))
                .andExpect(jsonPath("$[0].latency.avg", is(100.0)))
                // Window 2
                .andExpect(jsonPath("$[1].service", is("order-service")))
                .andExpect(jsonPath("$[1].totalEvents", is(1)))
                .andExpect(jsonPath("$[1].errorCount", is(0)))
                .andExpect(jsonPath("$[1].errorRate", is(0.0)))
                .andExpect(jsonPath("$[1].latency.count", is(1)))
                .andExpect(jsonPath("$[1].latency.avg", is(100.0)));

        // 2. Query summary metrics for order-service
        mockMvc.perform(get("/api/metrics/summary")
                        .param("service", "order-service")
                        .param("from", "2026-08-23T11:00:00Z")
                        .param("to", "2026-08-23T11:05:00Z")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service", is("order-service")))
                .andExpect(jsonPath("$.totalEvents", is(3)))
                .andExpect(jsonPath("$.errorCount", is(1)))
                .andExpect(jsonPath("$.errorRate", is(0.3333)))
                .andExpect(jsonPath("$.latency.count", is(3)))
                .andExpect(jsonPath("$.latency.min", is(50.0)))
                .andExpect(jsonPath("$.latency.max", is(150.0)))
                .andExpect(jsonPath("$.latency.avg", is(100.0)));

        // 3. Query distinct services
        mockMvc.perform(get("/api/metrics/services")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]", is("order-service")))
                .andExpect(jsonPath("$[1]", is("payment-service")));
    }
}
