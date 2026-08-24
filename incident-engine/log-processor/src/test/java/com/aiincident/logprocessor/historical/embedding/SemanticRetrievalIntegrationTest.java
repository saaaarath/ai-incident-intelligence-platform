package com.aiincident.logprocessor.historical.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SemanticRetrievalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("REST API: POST /api/knowledge/retrieve returns top-k semantically relevant documents")
    void testSemanticRetrievalPostEndpoint() throws Exception {
        SemanticRetrievalRequest request = new SemanticRetrievalRequest("HikariCP connection pool timeout in payment-service", 3);

        mockMvc.perform(post("/api/knowledge/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].documentId").exists())
                .andExpect(jsonPath("$[0].type").exists())
                .andExpect(jsonPath("$[0].similarityScore").value(greaterThan(0.1)))
                .andExpect(jsonPath("$[0].content").exists())
                .andExpect(jsonPath("$[0].matchedChunk").exists())
                .andExpect(jsonPath("$[0].metadata").exists());
    }

    @Test
    @DisplayName("REST API: GET /api/knowledge/retrieve performs similarity search via query params")
    void testSemanticRetrievalGetEndpoint() throws Exception {
        mockMvc.perform(get("/api/knowledge/retrieve")
                        .param("query", "Redis cache cluster timeout in inventory-service")
                        .param("topK", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].documentId").exists())
                .andExpect(jsonPath("$[0].similarityScore").value(greaterThan(0.1)));
    }

    @Test
    @DisplayName("REST API: GET /api/knowledge/similar-incidents retrieves similar historical incidents")
    void testSimilarIncidentsEndpoint() throws Exception {
        mockMvc.perform(get("/api/knowledge/similar-incidents")
                        .param("description", "Out of Memory heap space error in order-service")
                        .param("topK", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].type").value("HISTORICAL_INCIDENT"));
    }

    @Test
    @DisplayName("REST API: GET /api/knowledge/relevant-runbooks retrieves matching operational runbooks")
    void testRelevantRunbooksEndpoint() throws Exception {
        mockMvc.perform(get("/api/knowledge/relevant-runbooks")
                        .param("description", "Database connection pool exhausted HikariCP timeout")
                        .param("topK", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("RUNBOOK"))
                .andExpect(jsonPath("$[0].documentId").value("RB:RB-DB-001"));
    }

    @Test
    @DisplayName("REST API: GET /api/knowledge/relevant-postmortems retrieves matching postmortems")
    void testRelevantPostmortemsEndpoint() throws Exception {
        mockMvc.perform(get("/api/knowledge/relevant-postmortems")
                        .param("description", "Post-deployment 504 gateway timeout regression")
                        .param("topK", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("POSTMORTEM"));
    }
}
