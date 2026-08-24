package com.aiincident.logprocessor.historical.embedding;

import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.KnowledgeDocument;
import com.aiincident.logprocessor.historical.KnowledgeDocumentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    private DocumentChunker chunker;

    @BeforeEach
    void setUp() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setChunkSize(300);
        properties.setChunkOverlap(50);
        chunker = new DocumentChunker(properties, new ObjectMapper());
    }

    @Test
    @DisplayName("Verification: Section-based chunking splits by markdown headers and preserves metadata")
    void testSectionChunking() {
        String markdown = "# Runbook: Database Pool Exhaustion\n\n" +
                "## Trigger Symptoms\n- HikariPool connection timeout\n- HTTP 500 error spike\n\n" +
                "## Mitigation Steps\n1. Inspect active connections\n2. Terminate idle in transaction\n\n" +
                "## Escalation Path\nEscalate to DBA on call.";

        KnowledgeDocument doc = new KnowledgeDocument(
                "RB:RB-DB-001",
                KnowledgeDocumentType.RUNBOOK,
                "Runbook: Database Pool Exhaustion",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                null,
                Set.of("payment-service", "postgres"),
                Set.of("database", "pool"),
                markdown,
                null
        );

        List<DocumentChunker.DocumentChunk> chunks = chunker.chunkDocument(doc);
        assertThat(chunks).isNotEmpty();

        for (DocumentChunker.DocumentChunk chunk : chunks) {
            assertThat(chunk.getDocumentId()).isEqualTo("RB:RB-DB-001");
            assertThat(chunk.getDocumentType()).isEqualTo(KnowledgeDocumentType.RUNBOOK);
            assertThat(chunk.getChunkText()).isNotBlank();
            assertThat(chunk.getMetadata()).contains("DATABASE_CONNECTION_EXHAUSTION");
        }

        // Verify chunk indexing order
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getChunkIndex()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("Verification: Large text exceeding chunkSize splits with overlap")
    void testLargeTextChunkingWithOverlap() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Very Long Incident Document\n\n## Deep Technical Analysis\n");
        for (int i = 0; i < 20; i++) {
            sb.append("Detailed sentence number ").append(i).append(" analyzing the memory leak and thread contention. ");
        }

        KnowledgeDocument doc = new KnowledgeDocument(
                "INC:HIST-INC-001",
                KnowledgeDocumentType.HISTORICAL_INCIDENT,
                "Long Incident",
                HistoricalIncidentCategory.MEMORY_PRESSURE,
                null,
                Set.of("order-service"),
                Set.of("memory"),
                sb.toString(),
                null
        );

        List<DocumentChunker.DocumentChunk> chunks = chunker.chunkDocument(doc);
        assertThat(chunks.size()).isGreaterThan(1);
    }
}
