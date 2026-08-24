package com.aiincident.logprocessor.rca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration factory for instantiating the active LlmProvider.
 */
@Configuration
public class LlmProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderFactory.class);

    private final LlmProperties properties;

    public LlmProviderFactory(LlmProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean(LlmProvider.class)
    public LlmProvider llmProvider() {
        String provider = properties.getProvider() != null ? properties.getProvider().trim().toLowerCase() : "mock";

        switch (provider) {
            case "openai":
            case "gemini":
            case "ollama":
            case "http":
                log.info("Initializing HTTP LLM Provider: provider='{}', model='{}', url='{}'",
                        provider, properties.getModel(), properties.getApiUrl());
                return new HttpLlmProvider(properties);

            case "mock":
            case "deterministic":
            default:
                log.info("Initializing Deterministic Mock LLM Provider: model='{}'", properties.getModel());
                return new DeterministicMockLlmProvider(properties.getModel());
        }
    }
}
