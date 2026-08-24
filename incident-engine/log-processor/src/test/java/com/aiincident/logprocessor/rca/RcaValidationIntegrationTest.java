package com.aiincident.logprocessor.rca;

import com.aiincident.logging.LogEvent;
import com.aiincident.logprocessor.controller.RcaController;
import com.aiincident.logprocessor.service.LogProcessorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext
class RcaValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LogProcessorService logProcessorService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Seed logs to form incident
        Instant now = Instant.now();
        LogEvent payLog = new LogEvent(
                "ev-val-pay-1",
                now.minusSeconds(10),
                "payment-service",
                "ERROR",
                "DB_TIMEOUT",
                "tr-val-1",
                "Connection timeout in HikariPool",
                Map.of("pool", "HikariPool-1")
        );
        logProcessorService.processEvent(payLog);

        LogEvent ordLog = new LogEvent(
                "ev-val-ord-1",
                now,
                "order-service",
                "ERROR",
                "SERVICE_UNAVAILABLE",
                "tr-val-1",
                "Payment service returned 503",
                Map.of("statusCode", 503)
        );
        logProcessorService.processEvent(ordLog);
    }

    @Test
    @DisplayName("POST /api/incidents/validate-rca: Validates grounded report successfully")
    void testValidateRcaEndpoint_GroundedReport() throws Exception {
        RcaReport report = new RcaReport(
                new RcaReport.RootCause("Hikari pool exhaustion on payment-service", "DATABASE_CONNECTION_EXHAUSTION", "payment-service", "Observed timeout", true),
                new RcaReport.Confidence("HIGH", 0.95, "Observed direct log ev-val-pay-1 on root service"),
                List.of(new RcaReport.EvidenceItem("LOG", "payment-service", "HikariPool timeout", "ev-val-pay-1", true, Instant.now())),
                List.of(),
                new RcaReport.AffectedServices("payment-service", List.of("order-service"), Map.of()),
                List.of(),
                List.of(),
                List.of(),
                new RcaReport.RcaReportMetadata(Instant.now(), "mock", "mock-sre-engine", 100, "1")
        );

        RcaController.RcaValidationRequest request = new RcaController.RcaValidationRequest(report, null);

        mockMvc.perform(post("/api/incidents/validate-rca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.grounded", is(true)))
                .andExpect(jsonPath("$.status", is("VALID")));
    }

    @Test
    @DisplayName("POST /api/incidents/validate-rca: Detects and rejects invalid schema (empty rootCause)")
    void testValidateRcaEndpoint_InvalidSchema() throws Exception {
        RcaReport report = new RcaReport(
                new RcaReport.RootCause("", "UNKNOWN", "", "No details", false),
                new RcaReport.Confidence("LOW", 0.2, "Rationale"),
                List.of(),
                List.of(),
                new RcaReport.AffectedServices("payment-service", List.of(), Map.of()),
                List.of(),
                List.of(),
                List.of(),
                new RcaReport.RcaReportMetadata(Instant.now(), "mock", "mock-sre-engine", 100, "1")
        );

        RcaController.RcaValidationRequest request = new RcaController.RcaValidationRequest(report, null);

        mockMvc.perform(post("/api/incidents/validate-rca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.status", is("INVALID_SCHEMA")))
                .andExpect(jsonPath("$.errors", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/rca: End-to-end generated RCA report includes validation metadata")
    void testGenerateIncidentRca_IncludesValidationMetadata() throws Exception {
        mockMvc.perform(post("/api/incidents/1/rca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause.rootService", is("payment-service")))
                .andExpect(jsonPath("$.validation", notNullValue()))
                .andExpect(jsonPath("$.validation.valid", is(true)))
                .andExpect(jsonPath("$.validation.grounded", is(true)))
                .andExpect(jsonPath("$.validation.status", is("VALID")));
    }
}
