package com.aiincident.logprocessor.fingerprint;

import com.aiincident.logprocessor.anomaly.AnomalyRepository;
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
class ErrorFingerprintIntegrationTest {

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

    @Autowired
    private ErrorFingerprintGenerator fingerprintGenerator;

    @BeforeEach
    void setUp() {
        evidenceRepository.deleteAll();
        incidentRepository.deleteAll();
        anomalyRepository.deleteAll();
        logEventRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End: Normalization and fingerprinting REST endpoints and incident filtering by fingerprint")
    void testFingerprintingApiAndIncidentFiltering() throws Exception {
        Instant baseTime = Instant.parse("2026-08-23T12:00:00Z");

        // 1. Ingest multiple failure logs with different UUIDs and durations
        com.aiincident.logging.LogEvent event1 = new com.aiincident.logging.LogEvent(
                "ev-1", baseTime, "payment-service", "ERROR", "DB_TIMEOUT", "tr-100",
                "Failed payment for order aaaaaaaa-1111-2222-3333-444444444444: DB connection timeout after 3000ms", null
        );
        com.aiincident.logging.LogEvent event2 = new com.aiincident.logging.LogEvent(
                "ev-2", baseTime.plusSeconds(5), "payment-service", "ERROR", "DB_TIMEOUT", "tr-101",
                "Failed payment for order bbbbbbbb-5555-6666-7777-888888888888: DB connection timeout after 4500ms", null
        );

        logProcessorService.processEvent(event1);
        logProcessorService.processEvent(event2);

        // 2. Test POST /api/fingerprints/generate endpoint
        String requestJson = """
                {
                    "service": "payment-service",
                    "eventType": "DB_TIMEOUT",
                    "message": "Failed payment for order cccccccc-9999-0000-1111-222222222222: DB connection timeout after 5000ms"
                }
                """;

        String expectedHash = fingerprintGenerator.generateFingerprint(
                "payment-service",
                "DB_TIMEOUT",
                "Failed payment for order dddddddd-0000-0000-0000-000000000000: DB connection timeout after 1200ms"
        ).fingerprintHash();

        mockMvc.perform(post("/api/fingerprints/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fingerprintHash", is(expectedHash)))
                .andExpect(jsonPath("$.service", is("payment-service")))
                .andExpect(jsonPath("$.eventType", is("DB_TIMEOUT")))
                .andExpect(jsonPath("$.normalizedMessage", is("failed payment for order <uuid>: db connection timeout after <num>ms")));

        // 3. Test GET /api/fingerprints summary endpoint
        mockMvc.perform(get("/api/fingerprints")
                        .param("from", "2026-08-23T11:00:00Z")
                        .param("to", "2026-08-23T13:00:00Z")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fingerprintHash", is(expectedHash)))
                .andExpect(jsonPath("$[0].count", is(2)));

        // 4. Test GET /api/incidents?fingerprint=...
        mockMvc.perform(get("/api/incidents")
                        .param("fingerprint", expectedHash)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fingerprint", is(expectedHash)))
                .andExpect(jsonPath("$[0].primaryService", is("payment-service")));
    }
}
