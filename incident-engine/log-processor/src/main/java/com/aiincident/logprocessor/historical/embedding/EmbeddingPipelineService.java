package com.aiincident.logprocessor.historical.embedding;

import com.aiincident.logprocessor.historical.DocumentEmbedding;
import com.aiincident.logprocessor.historical.DocumentEmbeddingRepository;
import com.aiincident.logprocessor.historical.KnowledgeDocument;
import com.aiincident.logprocessor.historical.KnowledgeDocumentService;
import com.aiincident.logprocessor.historical.KnowledgeDocumentType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core orchestration pipeline for chunking knowledge documents, generating vector embeddings, and persisting them to pgvector.
 */
@Service
public class EmbeddingPipelineService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingPipelineService.class);

    private final DocumentChunker chunker;
    private final EmbeddingProvider embeddingProvider;
    private final DocumentEmbeddingRepository repository;
    private final KnowledgeDocumentService knowledgeService;
    private final EmbeddingProperties properties;

    public EmbeddingPipelineService(
            DocumentChunker chunker,
            EmbeddingProvider embeddingProvider,
            DocumentEmbeddingRepository repository,
            KnowledgeDocumentService knowledgeService,
            EmbeddingProperties properties) {
        this.chunker = chunker;
        this.embeddingProvider = embeddingProvider;
        this.repository = repository;
        this.knowledgeService = knowledgeService;
        this.properties = properties;
    }

    /**
     * Index a single operational knowledge document into vector chunks.
     */
    @Transactional
    public List<DocumentEmbedding> indexDocument(KnowledgeDocument document) {
        if (document == null) {
            return List.of();
        }

        // Remove previous chunks for idempotent update
        repository.deleteByDocumentId(document.getDocumentId());

        List<DocumentChunker.DocumentChunk> chunks = chunker.chunkDocument(document);
        List<DocumentEmbedding> embeddings = new ArrayList<>(chunks.size());

        for (DocumentChunker.DocumentChunk chunk : chunks) {
            float[] vector = embeddingProvider.generateEmbedding(chunk.getChunkText());

            // Validate dimension integrity
            validateDimension(vector, document.getDocumentId(), chunk.getChunkIndex());

            DocumentEmbedding embeddingEntity = new DocumentEmbedding(
                    chunk.getDocumentId(),
                    chunk.getDocumentType(),
                    chunk.getFullContent(),
                    chunk.getChunkText(),
                    vector,
                    chunk.getMetadata(),
                    chunk.getChunkIndex(),
                    Instant.now()
            );

            embeddings.add(embeddingEntity);
        }

        return repository.saveAll(embeddings);
    }

    /**
     * Run full embedding pipeline across all operational knowledge documents (incidents, postmortems, runbooks).
     */
    @Transactional
    public EmbeddingIndexingResult indexAllDocuments() {
        long startTime = System.currentTimeMillis();
        EmbeddingIndexingResult result = new EmbeddingIndexingResult();
        result.setProvider(embeddingProvider.getProviderName());
        result.setModel(embeddingProvider.getModelName());
        result.setDimension(embeddingProvider.getDimension());

        List<KnowledgeDocument> documents = knowledgeService.getAllDocuments();
        log.info("Starting embedding pipeline for {} operational knowledge documents (provider='{}', model='{}', dim={})...",
                documents.size(), embeddingProvider.getProviderName(), embeddingProvider.getModelName(), embeddingProvider.getDimension());

        int processed = 0;
        int chunksCreated = 0;
        int persisted = 0;

        for (KnowledgeDocument doc : documents) {
            try {
                List<DocumentEmbedding> saved = indexDocument(doc);
                processed++;
                chunksCreated += saved.size();
                persisted += saved.size();
            } catch (Exception e) {
                String errMsg = String.format("Failed to index document '%s': %s", doc.getDocumentId(), e.getMessage());
                log.warn(errMsg);
                result.addError(errMsg);
            }
        }

        result.setTotalDocumentsProcessed(processed);
        result.setTotalChunksCreated(chunksCreated);
        result.setTotalEmbeddingsPersisted(persisted);
        result.setDurationMs(System.currentTimeMillis() - startTime);

        if (!result.getErrors().isEmpty()) {
            result.setStatus(processed > 0 ? "PARTIAL_SUCCESS" : "FAILED");
        }

        log.info("Embedding pipeline completed: {} documents indexed, {} vector chunks persisted in {}ms (status={})",
                processed, persisted, result.getDurationMs(), result.getStatus());

        return result;
    }

    /**
     * Index knowledge documents filtered by type.
     */
    @Transactional
    public EmbeddingIndexingResult indexByType(KnowledgeDocumentType type) {
        long startTime = System.currentTimeMillis();
        EmbeddingIndexingResult result = new EmbeddingIndexingResult();
        result.setProvider(embeddingProvider.getProviderName());
        result.setModel(embeddingProvider.getModelName());
        result.setDimension(embeddingProvider.getDimension());

        List<KnowledgeDocument> documents = knowledgeService.search(null, type, null, null);
        int processed = 0;
        int persisted = 0;

        for (KnowledgeDocument doc : documents) {
            try {
                List<DocumentEmbedding> saved = indexDocument(doc);
                processed++;
                persisted += saved.size();
            } catch (Exception e) {
                String errMsg = String.format("Failed to index document '%s': %s", doc.getDocumentId(), e.getMessage());
                log.warn(errMsg);
                result.addError(errMsg);
            }
        }

        result.setTotalDocumentsProcessed(processed);
        result.setTotalChunksCreated(persisted);
        result.setTotalEmbeddingsPersisted(persisted);
        result.setDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private void validateDimension(float[] vector, String documentId, int chunkIndex) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException(String.format("Generated empty vector for document %s (chunk %d)", documentId, chunkIndex));
        }
        if (properties.getDimension() > 0 && vector.length != properties.getDimension()) {
            log.warn("Vector dimension mismatch for doc {} chunk {}: got {}, configured {}",
                    documentId, chunkIndex, vector.length, properties.getDimension());
        }
    }

    public EmbeddingProvider getEmbeddingProvider() {
        return embeddingProvider;
    }
}
