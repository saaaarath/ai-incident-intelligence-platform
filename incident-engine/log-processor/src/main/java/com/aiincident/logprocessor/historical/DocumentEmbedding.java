package com.aiincident.logprocessor.historical;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;

/**
 * Entity for storing text chunks and their associated vector embeddings in PostgreSQL (via pgvector).
 */
@Entity
@Table(
        name = "document_embeddings",
        indexes = {
                @Index(name = "idx_doc_embed_doc_id", columnList = "document_id"),
                @Index(name = "idx_doc_embed_type", columnList = "document_type")
        }
)
public class DocumentEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private KnowledgeDocumentType documentType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "chunk", nullable = false, columnDefinition = "TEXT")
    private String chunk;

    @Convert(converter = EmbeddingConverter.class)
    @Column(name = "embedding", columnDefinition = "TEXT")
    private float[] embedding;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public DocumentEmbedding() {
    }

    public DocumentEmbedding(
            String documentId,
            KnowledgeDocumentType documentType,
            String content,
            String chunk,
            float[] embedding,
            String metadata,
            Integer chunkIndex,
            Instant createdAt) {
        this.documentId = documentId;
        this.documentType = documentType;
        this.content = content;
        this.chunk = chunk;
        this.embedding = embedding != null ? Arrays.copyOf(embedding, embedding.length) : new float[0];
        this.metadata = metadata;
        this.chunkIndex = chunkIndex != null ? chunkIndex : 0;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public KnowledgeDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(KnowledgeDocumentType documentType) {
        this.documentType = documentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getChunk() {
        return chunk;
    }

    public void setChunk(String chunk) {
        this.chunk = chunk;
    }

    public float[] getEmbedding() {
        return embedding != null ? Arrays.copyOf(embedding, embedding.length) : new float[0];
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding != null ? Arrays.copyOf(embedding, embedding.length) : new float[0];
    }

    public int getEmbeddingDimension() {
        return embedding != null ? embedding.length : 0;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex != null ? chunkIndex : 0;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
