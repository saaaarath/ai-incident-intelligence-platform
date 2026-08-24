package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.historical.DocumentEmbedding;
import com.aiincident.logprocessor.historical.DocumentEmbeddingService;
import com.aiincident.logprocessor.historical.KnowledgeDocumentType;
import com.aiincident.logprocessor.historical.embedding.EmbeddingIndexingResult;
import com.aiincident.logprocessor.historical.embedding.EmbeddingPipelineService;
import com.aiincident.logprocessor.historical.embedding.EmbeddingProperties;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/embeddings", "/api/embeddings"})
public class EmbeddingController {

    private final EmbeddingPipelineService pipelineService;
    private final DocumentEmbeddingService embeddingService;
    private final EmbeddingProperties properties;

    public EmbeddingController(
            EmbeddingPipelineService pipelineService,
            DocumentEmbeddingService embeddingService,
            EmbeddingProperties properties) {
        this.pipelineService = pipelineService;
        this.embeddingService = embeddingService;
        this.properties = properties;
    }

    /**
     * Trigger embedding pipeline to chunk and vectorize all operational knowledge documents.
     * Example: POST /api/embeddings/index
     */
    @PostMapping("/index")
    public ResponseEntity<EmbeddingIndexingResult> indexEmbeddings(
            @RequestParam(required = false) String type) {

        KnowledgeDocumentType docType = KnowledgeDocumentType.fromString(type);
        EmbeddingIndexingResult result;
        if (docType != null) {
            result = pipelineService.indexByType(docType);
        } else {
            result = pipelineService.indexAllDocuments();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Query stored vector embeddings with optional filtering by documentId or documentType.
     * Example: GET /api/embeddings?documentId=RB:RB-DB-001
     */
    @GetMapping
    public ResponseEntity<List<DocumentEmbedding>> getEmbeddings(
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) String documentType) {

        if (documentId != null && !documentId.isBlank()) {
            return ResponseEntity.ok(embeddingService.getByDocumentId(documentId));
        }

        KnowledgeDocumentType type = KnowledgeDocumentType.fromString(documentType);
        if (type != null) {
            return ResponseEntity.ok(embeddingService.getByDocumentType(type));
        }

        return ResponseEntity.ok(embeddingService.getAllEmbeddings());
    }

    /**
     * Summary metrics and configuration metadata for the embedding pipeline.
     * Example: GET /api/embeddings/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getEmbeddingStats() {
        return ResponseEntity.ok(Map.of(
                "totalEmbeddings", embeddingService.count(),
                "provider", pipelineService.getEmbeddingProvider().getProviderName(),
                "model", pipelineService.getEmbeddingProvider().getModelName(),
                "dimension", pipelineService.getEmbeddingProvider().getDimension(),
                "chunkSize", properties.getChunkSize(),
                "chunkOverlap", properties.getChunkOverlap(),
                "autoIndexOnStartup", properties.isAutoIndexOnStartup()
        ));
    }
}
