package com.aiincident.inventoryservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiincident.failure.FailureInjectionService;
import com.aiincident.inventoryservice.entity.Inventory;
import com.aiincident.inventoryservice.repository.InventoryRepository;
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
class InventoryFailureInjectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private FailureInjectionService failureInjectionService;

    @BeforeEach
    void setup() {
        inventoryRepository.deleteAll();
        inventoryRepository.save(new Inventory("item-1", 100));
        failureInjectionService.disableFailure();
    }

    @Test
    void injectsServiceUnavailableAndRestoresNormalBehavior() throws Exception {
        // Normal request succeeds
        mockMvc.perform(get("/inventory/item-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(100));

        // Enable SERVICE_UNAVAILABLE via internal control API
        mockMvc.perform(post("/internal/failures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SERVICE_UNAVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // Requests fail with 503
        mockMvc.perform(get("/inventory/item-1"))
                .andExpect(status().isServiceUnavailable());

        // Control endpoint works
        mockMvc.perform(get("/internal/failures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // Disable failure
        mockMvc.perform(delete("/internal/failures"))
                .andExpect(status().isOk());

        // Normal behavior returns
        mockMvc.perform(get("/inventory/item-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(100));
    }

    @Test
    void injectsLatencyAndRestoresNormalBehavior() throws Exception {
        mockMvc.perform(post("/internal/failures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LATENCY\",\"latencyMs\":150}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.type").value("LATENCY"));

        long start = System.currentTimeMillis();
        mockMvc.perform(get("/inventory/item-1"))
                .andExpect(status().isOk());
        long elapsed = System.currentTimeMillis() - start;

        org.assertj.core.api.Assertions.assertThat(elapsed).isGreaterThanOrEqualTo(140L);

        // Reset
        mockMvc.perform(delete("/internal/failures"))
                .andExpect(status().isOk());
    }
}
