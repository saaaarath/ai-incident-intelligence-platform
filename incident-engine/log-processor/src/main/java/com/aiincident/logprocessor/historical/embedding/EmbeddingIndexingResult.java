package com.aiincident.logprocessor.historical.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary result of an embedding indexing pipeline execution.
 */
public class EmbeddingIndexingResult {

    private String status = "SUCCESS";
    private int totalDocumentsProcessed = 0;
    private int totalChunksCreated = 0;
    private int totalEmbeddingsPersisted = 0;
    private int dimension = 0;
    private String provider = "";
    private String model = "";
    private long durationMs = 0;
    private List<String> errors = new ArrayList<>();

    public EmbeddingIndexingResult() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotalDocumentsProcessed() {
        return totalDocumentsProcessed;
    }

    public void setTotalDocumentsProcessed(int totalDocumentsProcessed) {
        this.totalDocumentsProcessed = totalDocumentsProcessed;
    }

    public int getTotalChunksCreated() {
        return totalChunksCreated;
    }

    public void setTotalChunksCreated(int totalChunksCreated) {
        this.totalChunksCreated = totalChunksCreated;
    }

    public int getTotalEmbeddingsPersisted() {
        return totalEmbeddingsPersisted;
    }

    public void setTotalEmbeddingsPersisted(int totalEmbeddingsPersisted) {
        this.totalEmbeddingsPersisted = totalEmbeddingsPersisted;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }

    public void addError(String error) {
        if (error != null && !error.isBlank()) {
            this.errors.add(error);
        }
    }
}
