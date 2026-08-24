package com.aiincident.logprocessor.historical.embedding;

import com.aiincident.logprocessor.historical.DocumentEmbedding;
import com.aiincident.logprocessor.historical.DocumentEmbeddingRepository;
import com.aiincident.logprocessor.historical.DocumentEmbeddingService;
import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.KnowledgeDocument;
import com.aiincident.logprocessor.historical.KnowledgeDocumentService;
import com.aiincident.logprocessor.historical.KnowledgeDocumentType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for semantic similarity search over operational knowledge documents using pgvector embeddings.
 */
@Service
public class SemanticRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(SemanticRetrievalService.class);

    private final EmbeddingProvider embeddingProvider;
    private final DocumentEmbeddingRepository embeddingRepository;
    private final DocumentEmbeddingService vectorMathService;
    private final KnowledgeDocumentService knowledgeService;
    private final ObjectMapper objectMapper;

    public SemanticRetrievalService(
            EmbeddingProvider embeddingProvider,
            DocumentEmbeddingRepository embeddingRepository,
            DocumentEmbeddingService vectorMathService,
            KnowledgeDocumentService knowledgeService,
            ObjectMapper objectMapper) {
        this.embeddingProvider = embeddingProvider;
        this.embeddingRepository = embeddingRepository;
        this.vectorMathService = vectorMathService;
        this.knowledgeService = knowledgeService;
        this.objectMapper = objectMapper;
    }

    /**
     * Perform semantic similarity search with full request options.
     */
    public List<SemanticSearchResult> search(SemanticRetrievalRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return List.of();
        }

        String query = request.getQuery().trim();
        int topK = request.getTopK();
        double minScore = request.getMinScore();
        KnowledgeDocumentType typeFilter = request.getType();
        HistoricalIncidentCategory categoryFilter = request.getCategory();

        float[] queryVector;
        try {
            queryVector = embeddingProvider.generateEmbedding(query);
        } catch (Exception e) {
            log.warn("Notice: Semantic embedding generation unavailable ({}), returning fallback results.", e.getMessage());
            return List.of();
        }

        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }

        List<DocumentEmbedding> candidates;
        if (typeFilter != null) {
            candidates = embeddingRepository.findByDocumentType(typeFilter);
        } else {
            candidates = embeddingRepository.findAll();
        }

        if (candidates.isEmpty()) {
            log.debug("No candidate embeddings found in repository for semantic search.");
            return List.of();
        }

        // Group candidate chunks by documentId, selecting the best matching chunk per document
        Map<String, ScoredChunk> bestByDocument = new HashMap<>();

        for (DocumentEmbedding chunk : candidates) {
            if (chunk.getEmbedding() == null || chunk.getEmbedding().length == 0) {
                continue;
            }

            double similarity = vectorMathService.cosineSimilarity(queryVector, chunk.getEmbedding());
            if (similarity < minScore) {
                continue;
            }

            Map<String, Object> meta = parseMetadata(chunk.getMetadata());
            HistoricalIncidentCategory chunkCat = extractCategory(meta);

            if (categoryFilter != null && chunkCat != categoryFilter) {
                continue;
            }

            String docId = chunk.getDocumentId();
            ScoredChunk existing = bestByDocument.get(docId);
            if (existing == null || similarity > existing.similarity) {
                bestByDocument.put(docId, new ScoredChunk(chunk, similarity, meta, chunkCat));
            }
        }

        List<ScoredChunk> ranked = new ArrayList<>(bestByDocument.values());
        ranked.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        List<SemanticSearchResult> results = new ArrayList<>();
        int limit = Math.min(topK, ranked.size());

        for (int i = 0; i < limit; i++) {
            ScoredChunk sc = ranked.get(i);
            DocumentEmbedding chunk = sc.chunk;

            String title = (String) sc.metadata.getOrDefault("title", chunk.getDocumentId());
            String fullContent = chunk.getContent();

            // Enrich with canonical knowledge document content if available
            Optional<KnowledgeDocument> fullDoc = knowledgeService.getDocumentById(chunk.getDocumentId());
            if (fullDoc.isPresent()) {
                fullContent = fullDoc.get().getContent();
                title = fullDoc.get().getTitle();
            }

            results.add(new SemanticSearchResult(
                    chunk.getDocumentId(),
                    chunk.getDocumentType(),
                    roundScore(sc.similarity),
                    title,
                    sc.category,
                    fullContent,
                    chunk.getChunk(),
                    chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0,
                    sc.metadata
            ));
        }

        return results;
    }

    /**
     * Convenience search method with query string and optional type filter.
     */
    public List<SemanticSearchResult> search(String query, int topK, Double minScore, KnowledgeDocumentType typeFilter) {
        SemanticRetrievalRequest req = new SemanticRetrievalRequest(query, topK);
        if (minScore != null) {
            req.setMinScore(minScore);
        }
        req.setType(typeFilter);
        return search(req);
    }

    /**
     * Retrieve historically similar incidents matching an incident description.
     */
    public List<SemanticSearchResult> findSimilarIncidents(String incidentDescription, int topK) {
        return search(incidentDescription, topK, 0.0, KnowledgeDocumentType.HISTORICAL_INCIDENT);
    }

    /**
     * Retrieve operational runbooks semantically relevant to an incident description.
     */
    public List<SemanticSearchResult> findRelevantRunbooks(String incidentDescription, int topK) {
        return search(incidentDescription, topK, 0.0, KnowledgeDocumentType.RUNBOOK);
    }

    /**
     * Retrieve post-mortems semantically relevant to an incident description.
     */
    public List<SemanticSearchResult> findRelevantPostmortems(String incidentDescription, int topK) {
        return search(incidentDescription, topK, 0.0, KnowledgeDocumentType.POSTMORTEM);
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private HistoricalIncidentCategory extractCategory(Map<String, Object> meta) {
        Object catObj = meta.get("category");
        if (catObj instanceof String catStr) {
            return HistoricalIncidentCategory.fromString(catStr);
        }
        return null;
    }

    private double roundScore(double val) {
        return Math.round(val * 10000.0) / 10000.0;
    }

    private static class ScoredChunk {
        final DocumentEmbedding chunk;
        final double similarity;
        final Map<String, Object> metadata;
        final HistoricalIncidentCategory category;

        ScoredChunk(DocumentEmbedding chunk, double similarity, Map<String, Object> metadata, HistoricalIncidentCategory category) {
            this.chunk = chunk;
            this.similarity = similarity;
            this.metadata = metadata;
            this.category = category;
        }
    }
}
