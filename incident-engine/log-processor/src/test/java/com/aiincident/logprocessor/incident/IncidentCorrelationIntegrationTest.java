package com.aiincident.logprocessor.incident;

import com.aiincident.logprocessor.anomaly.AnomalyRepository;
import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.repository.LogEventRepository;
import com.aiincident.logprocessor.service.LogProcessorService;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class IncidentCorrelationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LogProcessorService logProcessorService;

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentEvidenceRepository evidenceRepository;

    @Autowired
    private AnomalyRepository anomalyRepository;

    @BeforeEach
    void setUp() {
        evidenceRepository.deleteAll();
        incidentRepository.deleteAll();
        anomalyRepository.deleteAll();
        logEventRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End: Controlled cascading failure scenario produces one unified incident with full evidence chain")
    void testControlledCascadingFailureScenario() throws Exception {
        // Cascading sequence from the assignment example:
        // 20:03:18 payment-service: DB timeout
        // 20:03:19 payment-service: pool exhausted
        // 20:03:21 payment-service: payment failure
        // 20:03:25 order-service: order timeout
        Instant t1 = Instant.parse("2026-08-23T20:03:18Z");
        Instant t2 = Instant.parse("2026-08-23T20:03:19Z");
        Instant t3 = Instant.parse("2026-08-23T20:03:21Z");
        Instant t4 = Instant.parse("2026-08-23T20:03:25Z");

        String sharedTraceId = "trace-cascade-789";

        com.aiincident.logging.LogEvent event1 = new com.aiincident.logging.LogEvent(
                "evt-1", t1, "payment-service", "ERROR", "DB_TIMEOUT", sharedTraceId, "DB timeout", null
        );
        com.aiincident.logging.LogEvent event2 = new com.aiincident.logging.LogEvent(
                "evt-2", t2, "payment-service", "ERROR", "POOL_EXHAUSTED", sharedTraceId, "pool exhausted", null
        );
        com.aiincident.logging.LogEvent event3 = new com.aiincident.logging.LogEvent(
                "evt-3", t3, "payment-service", "ERROR", "PAYMENT_FAILED", sharedTraceId, "payment failure", null
        );
        com.aiincident.logging.LogEvent event4 = new com.aiincident.logging.LogEvent(
                "evt-4", t4, "order-service", "ERROR", "ORDER_TIMEOUT", sharedTraceId, "order timeout", null
        );

        // Process all 4 cascading events through the log processor pipeline
        logProcessorService.processEvent(event1);
        logProcessorService.processEvent(event2);
        logProcessorService.processEvent(event3);
        logProcessorService.processEvent(event4);

        // 1. Verify exactly ONE incident was created for the whole cascade
        mockMvc.perform(get("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].primaryService", is("payment-service")))
                .andExpect(jsonPath("$[0].severity", is("CRITICAL")))
                .andExpect(jsonPath("$[0].status", is("OPEN")))
                .andExpect(jsonPath("$[0].startedAt", is("2026-08-23T20:03:18Z")))
                .andExpect(jsonPath("$[0].lastEventAt", is("2026-08-23T20:03:25Z")))
                .andExpect(jsonPath("$[0].affectedServices", hasItem("payment-service")))
                .andExpect(jsonPath("$[0].affectedServices", hasItem("order-service")));

        Long incidentId = incidentRepository.findAll().getFirst().getId();

        // 2. Query evidence endpoint and verify all 4 events are recorded in chronological order
        mockMvc.perform(get("/api/incidents/" + incidentId + "/evidence")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].eventType", is("DB_TIMEOUT")))
                .andExpect(jsonPath("$[0].service", is("payment-service")))
                .andExpect(jsonPath("$[0].timestamp", is("2026-08-23T20:03:18Z")))
                .andExpect(jsonPath("$[1].eventType", is("POOL_EXHAUSTED")))
                .andExpect(jsonPath("$[1].service", is("payment-service")))
                .andExpect(jsonPath("$[1].timestamp", is("2026-08-23T20:03:19Z")))
                .andExpect(jsonPath("$[2].eventType", is("PAYMENT_FAILED")))
                .andExpect(jsonPath("$[2].service", is("payment-service")))
                .andExpect(jsonPath("$[2].timestamp", is("2026-08-23T20:03:21Z")))
                .andExpect(jsonPath("$[3].eventType", is("ORDER_TIMEOUT")))
                .andExpect(jsonPath("$[3].service", is("order-service")))
                .andExpect(jsonPath("$[3].timestamp", is("2026-08-23T20:03:25Z")));

        // 3. Verify correlation endpoint POST /api/incidents/correlate
        mockMvc.perform(post("/api/incidents/correlate")
                        .param("from", "2026-08-23T20:00:00Z")
                        .param("to", "2026-08-23T20:10:00Z")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
