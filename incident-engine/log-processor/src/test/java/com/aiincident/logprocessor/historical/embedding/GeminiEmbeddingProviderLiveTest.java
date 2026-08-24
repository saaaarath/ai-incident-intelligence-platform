package com.aiincident.logprocessor.historical.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiEmbeddingProviderLiveTest {

    @Test
    @DisplayName("Live Integration: Generate real AI embedding from Google Gemini API")
    void testLiveGeminiEmbedding() {
        String apiKey = System.getenv("EMBEDDING_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            // Skip live network test if API key is not present in local environment
            return;
        }

        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setProvider("gemini");
        properties.setApiKey(apiKey);
        properties.setModel("gemini-embedding-001");
        properties.setDimension(768);
        properties.setApiUrl("https://generativelanguage.googleapis.com/v1beta");
        properties.setTimeoutMs(15000);

        HttpEmbeddingProvider provider = new HttpEmbeddingProvider(properties);

        String sampleText = "Payment service database connection pool timeout: HikariPool-1 connections exhausted.";
        float[] vector = provider.generateEmbedding(sampleText);

        assertThat(vector).isNotNull();
        assertThat(vector).hasSize(768);

        // Verify non-zero values
        boolean hasNonZero = false;
        for (float v : vector) {
            if (v != 0.0f) {
                hasNonZero = true;
                break;
            }
        }
        assertThat(hasNonZero).isTrue();

        System.out.println(">>> SUCCESSFULLY GENERATED LIVE GEMINI EMBEDDING <<<");
        System.out.println("Vector dimension: " + vector.length);
        System.out.printf("Sample vector coordinates: [%.4f, %.4f, %.4f, %.4f, ...]\n",
                vector[0], vector[1], vector[2], vector[3]);
    }
}
