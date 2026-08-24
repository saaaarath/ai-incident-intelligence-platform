package com.aiincident.logprocessor.historical;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class DocumentEmbeddingIntegrationTest {

    @Autowired
    private DocumentEmbeddingRepository repository;

    @Autowired
    private DocumentEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Verification: Store and retrieve document embedding with all required fields")
    void testStoreAndRetrieveDocumentEmbedding() {
        float[] vector = new float[]{0.123f, 0.456f, -0.789f, 0.012f, 0.999f};
        String metadataJson = "{\"category\":\"DATABASE_CONNECTION_EXHAUSTION\",\"service\":\"payment-service\",\"severity\":\"CRITICAL\"}";

        DocumentEmbedding embedding = new DocumentEmbedding(
                "INC:HIST-INC-001",
                KnowledgeDocumentType.HISTORICAL_INCIDENT,
                "# Incident: Payment Pool Saturation\nFull operational content...",
                "HikariPool connection timeout after 30000ms lease timeout",
                vector,
                metadataJson,
                0,
                Instant.now()
        );

        DocumentEmbedding saved = repository.save(embedding);
        assertThat(saved.getId()).isNotNull();

        DocumentEmbedding retrieved = repository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getDocumentId()).isEqualTo("INC:HIST-INC-001");
        assertThat(retrieved.getDocumentType()).isEqualTo(KnowledgeDocumentType.HISTORICAL_INCIDENT);
        assertThat(retrieved.getContent()).contains("Full operational content");
        assertThat(retrieved.getChunk()).isEqualTo("HikariPool connection timeout after 30000ms lease timeout");
        assertThat(retrieved.getMetadata()).contains("DATABASE_CONNECTION_EXHAUSTION");
        assertThat(retrieved.getChunkIndex()).isEqualTo(0);
        assertThat(retrieved.getCreatedAt()).isNotNull();

        // Verify embedding vector integrity
        assertThat(retrieved.getEmbeddingDimension()).isEqualTo(5);
        float[] retrievedVector = retrieved.getEmbedding();
        assertThat(retrievedVector).hasSize(5);
        assertThat(retrievedVector[0]).isCloseTo(0.123f, within(0.0001f));
        assertThat(retrievedVector[1]).isCloseTo(0.456f, within(0.0001f));
        assertThat(retrievedVector[2]).isCloseTo(-0.789f, within(0.0001f));
        assertThat(retrievedVector[3]).isCloseTo(0.012f, within(0.0001f));
        assertThat(retrievedVector[4]).isCloseTo(0.999f, within(0.0001f));
    }

    @Test
    @DisplayName("Verification: Query embeddings by documentId and documentType")
    void testQueryByDocumentIdAndType() {
        float[] v1 = new float[]{0.1f, 0.2f, 0.3f};
        float[] v2 = new float[]{0.4f, 0.5f, 0.6f};
        float[] v3 = new float[]{0.7f, 0.8f, 0.9f};

        DocumentEmbedding chunk0 = new DocumentEmbedding(
                "RB:RB-DB-001",
                KnowledgeDocumentType.RUNBOOK,
                "Runbook content",
                "Prerequisites: Check psql access",
                v1,
                "{\"service\":\"postgres\"}",
                0,
                Instant.now()
        );

        DocumentEmbedding chunk1 = new DocumentEmbedding(
                "RB:RB-DB-001",
                KnowledgeDocumentType.RUNBOOK,
                "Runbook content",
                "Mitigation: Kill idle transactions",
                v2,
                "{\"service\":\"postgres\"}",
                1,
                Instant.now()
        );

        DocumentEmbedding postmortem = new DocumentEmbedding(
                "PM:PM-HIST-INC-001",
                KnowledgeDocumentType.POSTMORTEM,
                "Postmortem content",
                "Root cause: Missing composite index",
                v3,
                "{\"incidentId\":\"HIST-INC-001\"}",
                0,
                Instant.now()
        );

        repository.saveAll(List.of(chunk0, chunk1, postmortem));

        List<DocumentEmbedding> rbChunks = repository.findByDocumentId("RB:RB-DB-001");
        assertThat(rbChunks).hasSize(2);

        List<DocumentEmbedding> runbookType = repository.findByDocumentType(KnowledgeDocumentType.RUNBOOK);
        assertThat(runbookType).hasSize(2);

        List<DocumentEmbedding> pmType = repository.findByDocumentType(KnowledgeDocumentType.POSTMORTEM);
        assertThat(pmType).hasSize(1);
        assertThat(pmType.getFirst().getDocumentId()).isEqualTo("PM:PM-HIST-INC-001");
    }

    @Test
    @DisplayName("Verification: Vector mathematical similarity operations")
    void testVectorMathOperations() {
        float[] vecA = new float[]{1.0f, 0.0f, 0.0f};
        float[] vecB = new float[]{1.0f, 0.0f, 0.0f};
        float[] vecC = new float[]{0.0f, 1.0f, 0.0f};
        float[] vecD = new float[]{-1.0f, 0.0f, 0.0f};

        // Identical vectors -> cosine similarity = 1.0
        double simIdentical = embeddingService.cosineSimilarity(vecA, vecB);
        assertThat(simIdentical).isCloseTo(1.0, within(0.0001));

        // Orthogonal vectors -> cosine similarity = 0.0
        double simOrthogonal = embeddingService.cosineSimilarity(vecA, vecC);
        assertThat(simOrthogonal).isCloseTo(0.0, within(0.0001));

        // Opposite vectors -> cosine similarity = -1.0
        double simOpposite = embeddingService.cosineSimilarity(vecA, vecD);
        assertThat(simOpposite).isCloseTo(-1.0, within(0.0001));

        // Euclidean distance
        double distIdentical = embeddingService.euclideanDistance(vecA, vecB);
        assertThat(distIdentical).isCloseTo(0.0, within(0.0001));

        double distOrthogonal = embeddingService.euclideanDistance(vecA, vecC);
        assertThat(distOrthogonal).isCloseTo(Math.sqrt(2.0), within(0.0001));

        // Dot product
        double dotIdentical = embeddingService.dotProduct(vecA, vecB);
        assertThat(dotIdentical).isCloseTo(1.0, within(0.0001));
    }
}
