package com.aiincident.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiincident.failure.pool.ConnectionPoolExhaustionSimulator;
import com.aiincident.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the payment-service database connection exhaustion scenario.
 *
 * <p>These tests verify that:
 * <ol>
 *   <li>When the pool is exhausted, payment creation fails with HTTP 503 and the
 *       correct error message (realistic failure — not a fake log).</li>
 *   <li>The control endpoint ({@code /internal/pool/exhaust}) is never blocked by
 *       the failure injection filter.</li>
 *   <li>Disabling exhaustion via the control API immediately restores normal
 *       payment creation (regression verification).</li>
 *   <li>The generic {@code /internal/failures} endpoint with type
 *       {@code CONNECTION_POOL_EXHAUSTED} also arms the simulator.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentConnectionPoolExhaustionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ConnectionPoolExhaustionSimulator poolSimulator;

    @BeforeEach
    void setup() {
        paymentRepository.deleteAll();
        // Always start each test with the pool in normal state.
        poolSimulator.disableExhaustion();
    }

    // -----------------------------------------------------------------------
    // 1. Normal behavior — baseline
    // -----------------------------------------------------------------------

    @Test
    void normalPaymentCreationSucceeds() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"amount\":\"50.00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1));
    }

    // -----------------------------------------------------------------------
    // 2. Exhaustion via /internal/pool/exhaust
    // -----------------------------------------------------------------------

    @Test
    void poolExhaustionViaPoolEndpointRejectsPayments() throws Exception {
        // Verify pool is healthy before injection.
        mockMvc.perform(get("/internal/pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exhausted").value(false));

        // Arm exhaustion — 0 connections available.
        mockMvc.perform(post("/internal/pool/exhaust")
                        .param("available", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exhausted").value(true))
                .andExpect(jsonPath("$.availableConnections").value(0));

        assertThat(poolSimulator.isExhausted()).isTrue();

        // Payment creation must fail with 503 and descriptive error.
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":2,\"amount\":\"30.00\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("connection pool exhaustion")));

        // The control endpoint itself must be reachable while exhaustion is active.
        mockMvc.perform(get("/internal/pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exhausted").value(true));
    }

    // -----------------------------------------------------------------------
    // 3. Restoration restores normal behavior (regression)
    // -----------------------------------------------------------------------

    @Test
    void disablingExhaustionRestoresNormalPaymentBehavior() throws Exception {
        // Enable exhaustion.
        mockMvc.perform(post("/internal/pool/exhaust")
                        .param("available", "0"))
                .andExpect(status().isOk());

        // Payment fails.
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":10,\"amount\":\"20.00\"}"))
                .andExpect(status().isServiceUnavailable());

        // Restore pool.
        mockMvc.perform(delete("/internal/pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exhausted").value(false));

        assertThat(poolSimulator.isExhausted()).isFalse();

        // Payment succeeds again.
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":11,\"amount\":\"20.00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(11));
    }

    // -----------------------------------------------------------------------
    // 4. Generic /internal/failures API also arms the pool simulator
    // -----------------------------------------------------------------------

    @Test
    void genericFailureApiArmsPoolSimulator() throws Exception {
        // Enable via generic API.
        mockMvc.perform(post("/internal/failures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CONNECTION_POOL_EXHAUSTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.type").value("CONNECTION_POOL_EXHAUSTED"));

        // Pool simulator must now be armed.
        assertThat(poolSimulator.isExhausted()).isTrue();

        // Payment fails.
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":20,\"amount\":\"15.00\"}"))
                .andExpect(status().isServiceUnavailable());

        // Disable via generic DELETE.
        mockMvc.perform(delete("/internal/failures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(poolSimulator.isExhausted()).isFalse();

        // Normal behavior restored.
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":21,\"amount\":\"15.00\"}"))
                .andExpect(status().isCreated());
    }

    // -----------------------------------------------------------------------
    // 5. Partial availability (some permits left) still allows connections
    // -----------------------------------------------------------------------

    @Test
    void partialPoolAvailabilityAllowsPayments() throws Exception {
        // Allow 5 connections — pool is not fully exhausted.
        mockMvc.perform(post("/internal/pool/exhaust")
                        .param("available", "5"))
                .andExpect(status().isOk());

        // Payments succeed because at least one permit is available.
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":30,\"amount\":\"10.00\"}"))
                .andExpect(status().isCreated());

        // Restore.
        mockMvc.perform(delete("/internal/pool"))
                .andExpect(status().isOk());
    }
}
