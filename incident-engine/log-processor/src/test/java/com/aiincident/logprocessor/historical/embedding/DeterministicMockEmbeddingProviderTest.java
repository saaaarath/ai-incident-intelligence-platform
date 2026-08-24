package com.aiincident.logprocessor.historical.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DeterministicMockEmbeddingProviderTest {

    private DeterministicMockEmbeddingProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DeterministicMockEmbeddingProvider(384, "mock-384");
    }

    @Test
    @DisplayName("Verification: Determinism (identical text generates identical vector)")
    void testDeterminism() {
        String text = "HikariCP pool timeout on payment-service";
        float[] v1 = provider.generateEmbedding(text);
        float[] v2 = provider.generateEmbedding(text);

        assertThat(v1).isEqualTo(v2);
        assertThat(v1).hasSize(384);
    }

    @Test
    @DisplayName("Verification: Dimension validation (output vector matches configured dimension)")
    void testDimensionValidation() {
        DeterministicMockEmbeddingProvider provider768 = new DeterministicMockEmbeddingProvider(768, "mock-768");
        float[] v768 = provider768.generateEmbedding("Database connection leak");
        assertThat(v768).hasSize(768);

        DeterministicMockEmbeddingProvider provider1536 = new DeterministicMockEmbeddingProvider(1536, "mock-1536");
        float[] v1536 = provider1536.generateEmbedding("Database connection leak");
        assertThat(v1536).hasSize(1536);
    }

    @Test
    @DisplayName("Verification: Unit normalization (L2 norm is approximately 1.0)")
    void testUnitLengthNormalization() {
        float[] vector = provider.generateEmbedding("Payment gateway 504 gateway timeout");

        double sumSq = 0.0;
        for (float f : vector) {
            sumSq += f * f;
        }
        double norm = Math.sqrt(sumSq);
        assertThat(norm).isCloseTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("Verification: Empty or null text returns zero vector")
    void testEmptyText() {
        float[] vNull = provider.generateEmbedding(null);
        float[] vBlank = provider.generateEmbedding("   ");

        assertThat(vNull).hasSize(384);
        assertThat(vBlank).hasSize(384);
    }
}
