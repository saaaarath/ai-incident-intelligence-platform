package com.aiincident.logprocessor.historical.embedding;

import com.aiincident.logprocessor.historical.DocumentEmbedding;
import com.aiincident.logprocessor.historical.DocumentEmbeddingRepository;
import com.aiincident.logprocessor.historical.DocumentEmbeddingService;
import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.KnowledgeDocument;
import com.aiincident.logprocessor.historical.KnowledgeDocumentService;
import com.aiincident.logprocessor.historical.KnowledgeDocumentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticRetrievalServiceTest {

    @Mock
    private DocumentEmbeddingRepository repository;

    @Mock
    private KnowledgeDocumentService knowledgeService;

    private EmbeddingProvider mockProvider;
    private DocumentEmbeddingService vectorMathService;
    private SemanticRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        mockProvider = new DeterministicMockEmbeddingProvider(384, "mock-384");
        vectorMathService = new DocumentEmbeddingService(repository);
        retrievalService = new SemanticRetrievalService(
                mockProvider,
                repository,
                vectorMathService,
                knowledgeService,
                new ObjectMapper()
        );
    }

    private DocumentEmbedding createChunk(String docId, KnowledgeDocumentType type, String text, HistoricalIncidentCategory category) {
        float[] vector = mockProvider.generateEmbedding(text);
        String metadata = String.format("{\"documentId\":\"%s\",\"type\":\"%s\",\"category\":\"%s\",\"title\":\"Title for %s\"}",
                docId, type.name(), category.name(), docId);
        return new DocumentEmbedding(docId, type, "Full content for " + docId, text, vector, metadata, 0, Instant.now());
    }

    @Test
    @DisplayName("Verification: Database connection pool issue ranks DB Runbook and DB Incidents highest")
    void testDatabaseConnectionSearchRanking() {
        DocumentEmbedding dbRunbook = createChunk("RB:RB-DB-001", KnowledgeDocumentType.RUNBOOK,
                "Runbook Database Connection Pool Exhaustion HikariPool timeout active connections", HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION);
        DocumentEmbedding dbIncident = createChunk("INC:HIST-INC-001", KnowledgeDocumentType.HISTORICAL_INCIDENT,
                "Payment Service Connection Pool Exhaustion HikariCP leak database timeout", HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION);
        DocumentEmbedding memIncident = createChunk("INC:HIST-INC-005", KnowledgeDocumentType.HISTORICAL_INCIDENT,
                "Order Service Java Heap Space Out of Memory OOM memory pressure", HistoricalIncidentCategory.MEMORY_PRESSURE);
        DocumentEmbedding depIncident = createChunk("INC:HIST-INC-002", KnowledgeDocumentType.HISTORICAL_INCIDENT,
                "Deployment regression 504 gateway timeout breaking API release", HistoricalIncidentCategory.DEPLOYMENT_REGRESSION);

        when(repository.findAll()).thenReturn(List.of(dbRunbook, dbIncident, memIncident, depIncident));

        String query = "HikariPool database connection timeout on payment-service";
        List<SemanticSearchResult> results = retrievalService.search(query, 5, 0.0, null);

        assertThat(results).isNotEmpty();
        // Top results must be the database related documents
        assertThat(results.get(0).getCategory()).isEqualTo(HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION);
        assertThat(results.get(1).getCategory()).isEqualTo(HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION);

        // Verify output properties
        SemanticSearchResult top = results.get(0);
        assertThat(top.getDocumentId()).isNotBlank();
        assertThat(top.getType()).isNotNull();
        assertThat(top.getSimilarityScore()).isGreaterThan(0.2);
        assertThat(top.getContent()).isNotBlank();
        assertThat(top.getMatchedChunk()).isNotBlank();
    }

    @Test
    @DisplayName("Verification: Memory pressure issue ranks Memory Runbook highest")
    void testMemoryPressureSearchRanking() {
        DocumentEmbedding memRunbook = createChunk("RB:RB-MEM-001", KnowledgeDocumentType.RUNBOOK,
                "Runbook High Memory Pressure Garbage Collection Pause Heap Out of Memory OOM", HistoricalIncidentCategory.MEMORY_PRESSURE);
        DocumentEmbedding dbRunbook = createChunk("RB:RB-DB-001", KnowledgeDocumentType.RUNBOOK,
                "Runbook Database Connection Pool Exhaustion HikariPool timeout", HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION);

        when(repository.findAll()).thenReturn(List.of(memRunbook, dbRunbook));

        String query = "High heap memory usage and OutOfMemoryError in order-service";
        List<SemanticSearchResult> results = retrievalService.search(query, 5, 0.0, null);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getDocumentId()).isEqualTo("RB:RB-MEM-001");
        assertThat(results.get(0).getCategory()).isEqualTo(HistoricalIncidentCategory.MEMORY_PRESSURE);
    }

    @Test
    @DisplayName("Verification: Top-K constraint limits result count exactly")
    void testTopKConstraint() {
        DocumentEmbedding c1 = createChunk("DOC-1", KnowledgeDocumentType.HISTORICAL_INCIDENT, "Incident 1 database timeout", HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION);
        DocumentEmbedding c2 = createChunk("DOC-2", KnowledgeDocumentType.HISTORICAL_INCIDENT, "Incident 2 database timeout", HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION);
        DocumentEmbedding c3 = createChunk("DOC-3", KnowledgeDocumentType.HISTORICAL_INCIDENT, "Incident 3 database timeout", HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION);

        when(repository.findAll()).thenReturn(List.of(c1, c2, c3));

        List<SemanticSearchResult> top1 = retrievalService.search("database timeout", 1, 0.0, null);
        List<SemanticSearchResult> top2 = retrievalService.search("database timeout", 2, 0.0, null);

        assertThat(top1).hasSize(1);
        assertThat(top2).hasSize(2);
    }

    @Test
    @DisplayName("Verification: Type filtering returns only requested document types")
    void testTypeFiltering() {
        DocumentEmbedding runbook = createChunk("RB:RB-DB-001", KnowledgeDocumentType.RUNBOOK, "Runbook database timeout", HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION);
        when(repository.findByDocumentType(eq(KnowledgeDocumentType.RUNBOOK))).thenReturn(List.of(runbook));

        List<SemanticSearchResult> runbookResults = retrievalService.findRelevantRunbooks("database timeout", 5);
        assertThat(runbookResults).allMatch(r -> r.getType() == KnowledgeDocumentType.RUNBOOK);
    }

    @Test
    @DisplayName("Verification: Empty or blank query returns empty results safely")
    void testEmptyQuery() {
        assertThat(retrievalService.search("", 5, 0.0, null)).isEmpty();
        assertThat(retrievalService.search(null, 5, 0.0, null)).isEmpty();
    }
}
