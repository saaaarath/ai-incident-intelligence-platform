package com.aiincident.logprocessor.historical.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the document embedding pipeline.
 */
@Component
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    /**
     * Provider type: "mock", "openai", "ollama", "local"
     */
    private String provider = "mock";

    /**
     * Model identifier, e.g. "mock-384", "text-embedding-3-small", "all-minilm-l6-v2"
     */
    private String model = "mock-384";

    /**
     * Target vector dimension (default 384)
     */
    private int dimension = 384;

    /**
     * Optional API key for remote providers (read via env EMBEDDING_API_KEY, never hardcoded)
     */
    private String apiKey = "";

    /**
     * Endpoint URL for remote embedding providers
     */
    private String apiUrl = "https://api.openai.com/v1/embeddings";

    /**
     * Maximum character count per document chunk
     */
    private int chunkSize = 500;

    /**
     * Overlap character count between consecutive chunks
     */
    private int chunkOverlap = 100;

    /**
     * Whether to automatically generate embeddings for operational knowledge documents on startup
     */
    private boolean autoIndexOnStartup = true;

    /**
     * Request timeout in milliseconds for remote provider calls
     */
    private int timeoutMs = 5000;

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

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public boolean isAutoIndexOnStartup() {
        return autoIndexOnStartup;
    }

    public void setAutoIndexOnStartup(boolean autoIndexOnStartup) {
        this.autoIndexOnStartup = autoIndexOnStartup;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
