package com.aiincident.logprocessor.historical.embedding;

import com.aiincident.logprocessor.historical.DocumentEmbedding;
import com.aiincident.logprocessor.historical.DocumentEmbeddingRepository;
import com.aiincident.logprocessor.historical.KnowledgeDocumentType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EmbeddingPipelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentEmbeddingRepository embeddingRepository;

    @Autowired
    private EmbeddingPipelineService pipelineService;

    @Test
    @DisplayName("Verification: Auto-indexed operational knowledge documents exist in document_embeddings")
    void testStartupAutoIndexingEmbeddings() {
        List<DocumentEmbedding> allEmbeddings = embeddingRepository.findAll();
        assertThat(allEmbeddings).isNotEmpty();

        // Verify representation of all 3 document types in vector space
        boolean hasIncidents = allEmbeddings.stream().anyMatch(e -> e.getDocumentType() == KnowledgeDocumentType.HISTORICAL_INCIDENT);
        boolean hasRunbooks = allEmbeddings.stream().anyMatch(e -> e.getDocumentType() == KnowledgeDocumentType.RUNBOOK);
        boolean hasPostmortems = allEmbeddings.stream().anyMatch(e -> e.getDocumentType() == KnowledgeDocumentType.POSTMORTEM);

        assertThat(hasIncidents).isTrue();
        assertThat(hasRunbooks).isTrue();
        assertThat(hasPostmortems).isTrue();

        // Verify dimension validation
        for (DocumentEmbedding embedding : allEmbeddings) {
            assertThat(embedding.getEmbeddingDimension()).isEqualTo(384);
            assertThat(embedding.getChunk()).isNotBlank();
            assertThat(embedding.getMetadata()).isNotBlank();
        }
    }

    @Test
    @DisplayName("REST API: POST /api/embeddings/index triggers manual re-indexing")
    void testTriggerManualIndexingEndpoint() throws Exception {
        mockMvc.perform(post("/api/embeddings/index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalDocumentsProcessed").value(greaterThanOrEqualTo(30)))
                .andExpect(jsonPath("$.totalEmbeddingsPersisted").value(greaterThanOrEqualTo(30)))
                .andExpect(jsonPath("$.dimension").value(384));
    }

    @Test
    @DisplayName("REST API: GET /api/embeddings retrieves stored vector records")
    void testGetEmbeddingsEndpoint() throws Exception {
        mockMvc.perform(get("/api/embeddings")
                        .param("documentId", "RB:RB-DB-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value("RB:RB-DB-001"))
                .andExpect(jsonPath("$[0].documentType").value("RUNBOOK"))
                .andExpect(jsonPath("$[0].chunk").exists());
    }

    @Test
    @DisplayName("REST API: GET /api/embeddings/stats returns pipeline configuration and metrics")
    void testGetEmbeddingStatsEndpoint() throws Exception {
        mockMvc.perform(get("/api/embeddings/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmbeddings").value(greaterThanOrEqualTo(30)))
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.dimension").value(384))
                .andExpect(jsonPath("$.autoIndexOnStartup").value(true));
    }
}
