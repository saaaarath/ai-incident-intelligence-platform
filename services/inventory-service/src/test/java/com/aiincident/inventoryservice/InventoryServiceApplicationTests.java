package com.aiincident.inventoryservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class InventoryServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void cleanDatabase() {
        inventoryRepository.deleteAll();
        inventoryRepository.save(new Inventory("product-1", 10));
    }

    @Test
    void retrievesInventory() throws Exception {
        mockMvc.perform(get("/inventory/product-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("product-1"))
                .andExpect(jsonPath("$.availableQuantity").value(10))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void reservesInventoryWhenStockIsSufficient() throws Exception {
        mockMvc.perform(post("/inventory/product-1/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(6));

        mockMvc.perform(get("/inventory/product-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(6));
    }

    @Test
    void rejectsReservationWhenStockIsInsufficient() throws Exception {
        mockMvc.perform(post("/inventory/product-1/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":11}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "Insufficient inventory for product product-1: requested 11, available 10"));

        mockMvc.perform(get("/inventory/product-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(10));
    }

    @Test
    void returnsNotFoundForUnknownProduct() throws Exception {
        mockMvc.perform(get("/inventory/unknown-product"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Inventory not found: unknown-product"));
    }

    @Test
    void rejectsInvalidReservationQuantity() throws Exception {
        mockMvc.perform(post("/inventory/product-1/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
