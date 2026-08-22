package com.aiincident.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiincident.failure.FailureInjectionService;
import com.aiincident.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFailureInjectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FailureInjectionService failureInjectionService;

    @BeforeEach
    void setup() {
        paymentRepository.deleteAll();
        failureInjectionService.disableFailure();
    }

    @Test
    void injectsServiceUnavailableAndRestoresNormalBehavior() throws Exception {
        // Normal behavior prior to failure injection
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"amount\":\"15.00\"}"))
                .andExpect(status().isCreated());

        // Enable SERVICE_UNAVAILABLE via internal API
        mockMvc.perform(post("/internal/failures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SERVICE_UNAVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.type").value("SERVICE_UNAVAILABLE"));

        // Production request fails with 503 Service Unavailable
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":2,\"amount\":\"20.00\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service is temporarily unavailable (simulated failure)"));

        // Control endpoint /internal/failures is unaffected by failure injection
        mockMvc.perform(get("/internal/failures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // Disable failure via internal API
        mockMvc.perform(delete("/internal/failures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Normal behavior returns
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":3,\"amount\":\"25.00\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void injectsDbFailureAndErrorSpike() throws Exception {
        // Test DB_FAILURE
        mockMvc.perform(post("/internal/failures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DB_FAILURE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":10,\"amount\":\"10.00\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Simulated database operation failure"));

        // Test ERROR_SPIKE
        mockMvc.perform(post("/internal/failures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ERROR_SPIKE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":11,\"amount\":\"10.00\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Simulated internal error spike"));

        // Disable failure
        mockMvc.perform(delete("/internal/failures"))
                .andExpect(status().isOk());
    }

    @Test
    void injectsLatencyWithConfigurableDuration() throws Exception {
        mockMvc.perform(post("/internal/failures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LATENCY\",\"latencyMs\":150}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.type").value("LATENCY"))
                .andExpect(jsonPath("$.latencyMs").value(150));

        long start = System.currentTimeMillis();
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":50,\"amount\":\"10.00\"}"))
                .andExpect(status().isCreated());
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(140L);

        // Reset
        mockMvc.perform(delete("/internal/failures"))
                .andExpect(status().isOk());
    }
}
