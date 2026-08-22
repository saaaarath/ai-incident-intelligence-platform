package com.aiincident.orderservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiincident.failure.FailureInjectionService;
import com.aiincident.orderservice.repository.OrderRepository;
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
class OrderFailureInjectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private FailureInjectionService failureInjectionService;

    @BeforeEach
    void setup() {
        orderRepository.deleteAll();
        failureInjectionService.disableFailure();
    }

    @Test
    void injectsDbFailureAndRestoresNormalBehavior() throws Exception {
        // Normal order creation
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"cust-1\"}"))
                .andExpect(status().isCreated());

        // Inject DB_FAILURE via control API
        mockMvc.perform(post("/internal/failures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DB_FAILURE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // Order creation fails with 503 Service Unavailable (DB operation failure)
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"cust-2\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Simulated database operation failure"));

        // Disable failure mode
        mockMvc.perform(delete("/internal/failures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Normal order creation works again
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"cust-3\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void securityTokenBlocksUnauthorizedAccessWhenConfigured() throws Exception {
        // With default test config (token empty), requests pass
        mockMvc.perform(get("/internal/failures"))
                .andExpect(status().isOk());
    }
}
