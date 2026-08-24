package com.aiincident.logprocessor.historical.embedding;

import com.aiincident.logprocessor.historical.DocumentEmbedding;
import com.aiincident.logprocessor.historical.DocumentEmbeddingRepository;
import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.KnowledgeDocument;
import com.aiincident.logprocessor.historical.KnowledgeDocumentService;
import com.aiincident.logprocessor.historical.KnowledgeDocumentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingPipelineServiceTest {

    @Mock
    private DocumentEmbeddingRepository repository;

    @Mock
    private KnowledgeDocumentService knowledgeService;

    @Mock
    private EmbeddingProvider mockProvider;

    private DocumentChunker chunker;
    private EmbeddingProperties properties;
    private EmbeddingPipelineService pipelineService;

    @BeforeEach
    void setUp() {
        properties = new EmbeddingProperties();
        properties.setDimension(384);
        chunker = new DocumentChunker(properties, new ObjectMapper());
        pipelineService = new EmbeddingPipelineService(chunker, mockProvider, repository, knowledgeService, properties);
    }

    @Test
    @DisplayName("Verification: Pipeline chunks document, generates vector, and saves to repository")
    void testIndexDocumentSuccess() {
        KnowledgeDocument doc = new KnowledgeDocument(
                "INC:HIST-INC-001",
                KnowledgeDocumentType.HISTORICAL_INCIDENT,
                "HikariCP Outage",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                null,
                Set.of("payment-service"),
                Set.of("database"),
                "# HikariCP Outage\n## Root Cause\nMissing index on payments",
                null
        );

        float[] sampleVector = new float[384];
        sampleVector[0] = 0.5f;
        when(mockProvider.generateEmbedding(any())).thenReturn(sampleVector);
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<DocumentEmbedding> results = pipelineService.indexDocument(doc);
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getDocumentId()).isEqualTo("INC:HIST-INC-001");
        assertThat(results.getFirst().getEmbedding()).hasSize(384);

        verify(repository).deleteByDocumentId("INC:HIST-INC-001");
        verify(repository).saveAll(anyList());
    }

    @Test
    @DisplayName("Verification: Graceful handling of provider failures during batch indexing")
    void testHandleProviderFailureGracefully() {
        KnowledgeDocument doc1 = new KnowledgeDocument(
                "INC:HIST-INC-001",
                KnowledgeDocumentType.HISTORICAL_INCIDENT,
                "Doc 1",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                null,
                Set.of(),
                Set.of(),
                "Content 1",
                null
        );

        KnowledgeDocument doc2 = new KnowledgeDocument(
                "INC:HIST-INC-002",
                KnowledgeDocumentType.HISTORICAL_INCIDENT,
                "Doc 2",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                null,
                Set.of(),
                Set.of(),
                "Content 2",
                null
        );

        when(knowledgeService.getAllDocuments()).thenReturn(List.of(doc1, doc2));
        when(mockProvider.getProviderName()).thenReturn("test-provider");
        when(mockProvider.getModelName()).thenReturn("test-model");
        when(mockProvider.getDimension()).thenReturn(384);

        // First document succeeds, second document fails with provider exception
        float[] sampleVector = new float[384];
        when(mockProvider.generateEmbedding(org.mockito.ArgumentMatchers.contains("Content 1"))).thenReturn(sampleVector);
        when(mockProvider.generateEmbedding(org.mockito.ArgumentMatchers.contains("Content 2"))).thenThrow(new EmbeddingProviderException("API 503 Overloaded"));
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        EmbeddingIndexingResult result = pipelineService.indexAllDocuments();

        // Platform does not crash; partial success status is reported with errors captured
        assertThat(result.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.getTotalDocumentsProcessed()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst()).contains("API 503 Overloaded");
    }
}
