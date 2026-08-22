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

    @Test
    void reservesInventoryWithGeneratedTraceId() throws Exception {
        org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger(com.aiincident.inventoryservice.service.InventoryService.class);
        ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) slf4jLogger;
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender = new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        logbackLogger.addAppender(listAppender);

        try {
            org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(post("/inventory/product-1/reserve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"quantity\":2}"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Trace-Id"))
                    .andReturn();

            String traceId = result.getResponse().getHeader("X-Trace-Id");
            org.assertj.core.api.Assertions.assertThat(traceId).isNotBlank();

            org.assertj.core.api.Assertions.assertThat(listAppender.list).isNotEmpty();
            String loggedJson = listAppender.list.getFirst().getMessage();
            org.assertj.core.api.Assertions.assertThat(loggedJson).contains("\"traceId\":\"" + traceId + "\"");
            org.assertj.core.api.Assertions.assertThat(loggedJson).contains("\"eventType\":\"INVENTORY_RESERVED\"");
        } finally {
            logbackLogger.detachAppender(listAppender);
        }
    }

    @Test
    void reservesInventoryWithSuppliedTraceId() throws Exception {
        org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger(com.aiincident.inventoryservice.service.InventoryService.class);
        ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) slf4jLogger;
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender = new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        logbackLogger.addAppender(listAppender);

        try {
            mockMvc.perform(post("/inventory/product-1/reserve")
                            .header("X-Trace-Id", "custom-inv-trace-999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"quantity\":2}"))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Trace-Id", "custom-inv-trace-999"));

            org.assertj.core.api.Assertions.assertThat(listAppender.list).isNotEmpty();
            String loggedJson = listAppender.list.getFirst().getMessage();
            org.assertj.core.api.Assertions.assertThat(loggedJson).contains("\"traceId\":\"custom-inv-trace-999\"");
            org.assertj.core.api.Assertions.assertThat(loggedJson).contains("\"eventType\":\"INVENTORY_RESERVED\"");
        } finally {
            logbackLogger.detachAppender(listAppender);
        }
    }
}
