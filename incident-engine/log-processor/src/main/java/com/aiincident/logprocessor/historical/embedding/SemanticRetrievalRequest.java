package com.aiincident.logprocessor.historical.embedding;

import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.KnowledgeDocumentType;

/**
 * Request payload for semantic similarity search over operational knowledge.
 */
public class SemanticRetrievalRequest {

    /**
     * Incident description or operational search query (required)
     */
    private String query;

    /**
     * Maximum number of documents to return (default 5)
     */
    private Integer topK = 5;

    /**
     * Minimum cosine similarity score threshold (0.0 - 1.0)
     */
    private Double minScore = 0.0;

    /**
     * Optional filter by document type (HISTORICAL_INCIDENT, RUNBOOK, POSTMORTEM)
     */
    private KnowledgeDocumentType type;

    /**
     * Optional filter by failure category
     */
    private HistoricalIncidentCategory category;

    public SemanticRetrievalRequest() {
    }

    public SemanticRetrievalRequest(String query) {
        this.query = query;
    }

    public SemanticRetrievalRequest(String query, Integer topK) {
        this.query = query;
        this.topK = topK;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK != null && topK > 0 ? topK : 5;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Double getMinScore() {
        return minScore != null ? minScore : 0.0;
    }

    public void setMinScore(Double minScore) {
        this.minScore = minScore;
    }

    public KnowledgeDocumentType getType() {
        return type;
    }

    public void setType(KnowledgeDocumentType type) {
        this.type = type;
    }

    public HistoricalIncidentCategory getCategory() {
        return category;
    }

    public void setCategory(HistoricalIncidentCategory category) {
        this.category = category;
    }
}
