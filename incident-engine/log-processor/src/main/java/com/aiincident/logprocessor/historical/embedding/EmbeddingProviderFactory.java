package com.aiincident.logprocessor.historical.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration factory for instantiating the active EmbeddingProvider.
 */
@Configuration
public class EmbeddingProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingProviderFactory.class);

    private final EmbeddingProperties properties;

    public EmbeddingProviderFactory(EmbeddingProperties properties) {
        this.properties = properties;
    }

    @Bean
    public EmbeddingProvider embeddingProvider() {
        String provider = properties.getProvider() != null ? properties.getProvider().trim().toLowerCase() : "mock";

        switch (provider) {
            case "gemini":
            case "openai":
            case "ollama":
            case "http":
                log.info("Initializing HTTP EmbeddingProvider: provider='{}', model='{}', dimension={}, url='{}'",
                        provider, properties.getModel(), properties.getDimension(), properties.getApiUrl());
                return new HttpEmbeddingProvider(properties);

            case "mock":
            case "deterministic":
            case "local":
            default:
                log.info("Initializing Deterministic Mock EmbeddingProvider: model='{}', dimension={}",
                        properties.getModel(), properties.getDimension());
                return new DeterministicMockEmbeddingProvider(properties.getDimension(), properties.getModel());
        }
    }
}
