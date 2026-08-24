package com.aiincident.logprocessor.historical;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing, persisting, and querying document embeddings and vector
 * operations.
 */
@Service
public class DocumentEmbeddingService {

    private final DocumentEmbeddingRepository repository;

    public DocumentEmbeddingService(DocumentEmbeddingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DocumentEmbedding save(DocumentEmbedding embedding) {
        return repository.save(embedding);
    }

    @Transactional
    public List<DocumentEmbedding> saveAll(List<DocumentEmbedding> embeddings) {
        return repository.saveAll(embeddings);
    }

    @Transactional(readOnly = true)
    public List<DocumentEmbedding> getAllEmbeddings() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<DocumentEmbedding> getById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<DocumentEmbedding> getByDocumentId(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return List.of();
        }
        return repository.findByDocumentId(documentId.trim());
    }

    @Transactional(readOnly = true)
    public List<DocumentEmbedding> getByDocumentType(KnowledgeDocumentType documentType) {
        if (documentType == null) {
            return repository.findAll();
        }
        return repository.findByDocumentType(documentType);
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }

    @Transactional
    public void deleteByDocumentId(String documentId) {
        if (documentId != null && !documentId.isBlank()) {
            repository.deleteByDocumentId(documentId.trim());
        }
    }

    /**
     * Compute cosine similarity between two float vectors.
     * Range: [-1.0, 1.0] where 1.0 is identical orientation.
     */
    public double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length == 0 || vectorB.length == 0) {
            return 0.0;
        }
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException(
                    String.format("Vector dimension mismatch: %d vs %d", vectorA.length, vectorB.length));
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Compute Euclidean (L2) distance between two float vectors.
     */
    public double euclideanDistance(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must be non-null and have identical dimension");
        }

        double sum = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            double diff = vectorA[i] - vectorB[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * Compute dot product between two float vectors.
     */
    public double dotProduct(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must be non-null and have identical dimension");
        }

        double dot = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dot += vectorA[i] * vectorB[i];
        }
        return dot;
    }
}
