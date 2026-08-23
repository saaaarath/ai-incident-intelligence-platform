package com.aiincident.logprocessor.dependency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class ServiceDependencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceDependencyService dependencyService;

    @Test
    @DisplayName("End-to-End: Retrieve service dependencies via REST API")
    void testDependencyRetrievalEndpoints() throws Exception {
        // 1. GET /api/dependencies - check all initial seeded dependencies
        mockMvc.perform(get("/api/dependencies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sourceService=='order-service' && @.targetService=='payment-service')]").exists())
                .andExpect(jsonPath("$[?(@.sourceService=='payment-service' && @.targetService=='inventory-service')]").exists())
                .andExpect(jsonPath("$[?(@.sourceService=='payment-service' && @.targetService=='postgres')]").exists());

        // 2. GET /api/dependencies/{service} - topology for payment-service
        mockMvc.perform(get("/api/dependencies/payment-service")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service", is("payment-service")))
                .andExpect(jsonPath("$.downstream", hasItem("inventory-service")))
                .andExpect(jsonPath("$.downstream", hasItem("postgres")))
                .andExpect(jsonPath("$.upstream", hasItem("order-service")));

        // 3. GET /api/dependencies/{service}/downstream
        mockMvc.perform(get("/api/dependencies/payment-service/downstream")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.targetService=='inventory-service')]").exists());

        // 4. GET /api/dependencies/{service}/upstream
        mockMvc.perform(get("/api/dependencies/payment-service/upstream")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sourceService=='order-service')]").exists());

        // 5. POST /api/dependencies - add a new dependency
        String newDepJson = """
                {
                    "sourceService": "analytics-service",
                    "targetService": "kafka-cluster",
                    "dependencyType": "MESSAGE_QUEUE",
                    "description": "Analytics event consumer"
                }
                """;

        mockMvc.perform(post("/api/dependencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newDepJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceService", is("analytics-service")))
                .andExpect(jsonPath("$.targetService", is("kafka-cluster")))
                .andExpect(jsonPath("$.dependencyType", is("MESSAGE_QUEUE")));

        // 6. DELETE /api/dependencies - remove the dependency
        mockMvc.perform(delete("/api/dependencies")
                        .param("source", "analytics-service")
                        .param("target", "kafka-cluster"))
                .andExpect(status().isNoContent());
    }
}
