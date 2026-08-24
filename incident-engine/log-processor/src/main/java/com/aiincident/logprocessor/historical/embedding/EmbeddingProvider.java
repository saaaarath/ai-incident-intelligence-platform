package com.aiincident.logprocessor.historical.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface defining embedding generation capabilities across mock and external providers.
 */
public interface EmbeddingProvider {

    /**
     * Generate a float vector embedding for a single text input.
     */
    float[] generateEmbedding(String text);

    /**
     * Batch generate float vector embeddings for a list of text inputs.
     */
    default List<float[]> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(generateEmbedding(text));
        }
        return results;
    }

    /**
     * Configured vector dimension for this provider.
     */
    int getDimension();

    /**
     * Provider identifier name (e.g. "mock", "openai", "ollama").
     */
    String getProviderName();

    /**
     * Model identifier name.
     */
    String getModelName();
}
