package com.aiincident.logprocessor.rca;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the LLM Root Cause Analysis (RCA) Engine.
 */
@Component
@ConfigurationProperties(prefix = "ai.rca")
public class LlmProperties {

    /**
     * LLM provider type: "mock", "openai", "gemini", "ollama", "http"
     */
    private String provider = "mock";

    /**
     * Model identifier, e.g. "mock-sre-engine", "gpt-4o", "gpt-4o-mini", "gemini-1.5-flash", "llama3"
     */
    private String model = "mock-sre-engine";

    /**
     * API key for remote providers (never hardcoded, set via AI_RCA_API_KEY environment variable)
     */
    private String apiKey = "";

    /**
     * Base URL for the remote LLM endpoint
     */
    private String apiUrl = "https://api.openai.com/v1/chat/completions";

    /**
     * Temperature for LLM reasoning (default low temperature 0.1 for high determinism and grounded reasoning)
     */
    private double temperature = 0.1;

    /**
     * Maximum tokens to generate
     */
    private int maxTokens = 8192;

    /**
     * Request timeout in milliseconds
     */
    private int timeoutMs = 15000;

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

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
