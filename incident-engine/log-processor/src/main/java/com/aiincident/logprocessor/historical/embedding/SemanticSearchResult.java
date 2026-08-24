package com.aiincident.logprocessor.historical.embedding;

import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.KnowledgeDocumentType;
import java.util.Map;

/**
 * Result DTO representing a semantically retrieved operational knowledge document.
 */
public class SemanticSearchResult {

    private String documentId;
    private KnowledgeDocumentType type;
    private double similarityScore;
    private String title;
    private HistoricalIncidentCategory category;
    private String content;
    private String matchedChunk;
    private int chunkIndex;
    private Map<String, Object> metadata;

    public SemanticSearchResult() {
    }

    public SemanticSearchResult(
            String documentId,
            KnowledgeDocumentType type,
            double similarityScore,
            String title,
            HistoricalIncidentCategory category,
            String content,
            String matchedChunk,
            int chunkIndex,
            Map<String, Object> metadata) {
        this.documentId = documentId;
        this.type = type;
        this.similarityScore = similarityScore;
        this.title = title;
        this.category = category;
        this.content = content;
        this.matchedChunk = matchedChunk;
        this.chunkIndex = chunkIndex;
        this.metadata = metadata;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public KnowledgeDocumentType getType() {
        return type;
    }

    public void setType(KnowledgeDocumentType type) {
        this.type = type;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public HistoricalIncidentCategory getCategory() {
        return category;
    }

    public void setCategory(HistoricalIncidentCategory category) {
        this.category = category;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMatchedChunk() {
        return matchedChunk;
    }

    public void setMatchedChunk(String matchedChunk) {
        this.matchedChunk = matchedChunk;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
