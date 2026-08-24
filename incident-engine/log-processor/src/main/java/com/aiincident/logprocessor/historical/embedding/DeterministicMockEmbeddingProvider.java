package com.aiincident.logprocessor.historical.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Deterministic, unit-normalized mock embedding provider for tests and offline operations.
 * Projects input text into a reproducible float vector of configured dimension using hash projection.
 */
public class DeterministicMockEmbeddingProvider implements EmbeddingProvider {

    private final int dimension;
    private final String modelName;

    public DeterministicMockEmbeddingProvider(int dimension, String modelName) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("Dimension must be positive: " + dimension);
        }
        this.dimension = dimension;
        this.modelName = modelName != null && !modelName.isBlank() ? modelName : "mock-" + dimension;
    }

    public DeterministicMockEmbeddingProvider() {
        this(384, "mock-384");
    }

    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return createZeroVector();
        }

        float[] vector = new float[dimension];
        String normalized = text.toLowerCase(Locale.ROOT).trim();
        String[] tokens = normalized.split("[\\s\\p{Punct}]+");

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            projectToken(token, vector);
        }

        // Project full string hash to preserve global context
        projectToken(normalized, vector);

        // Normalize to unit length (L2 norm = 1.0)
        normalizeVector(vector);

        // Validate dimension integrity
        if (vector.length != dimension) {
            throw new IllegalStateException(String.format("Generated vector dimension %d does not match configured %d", vector.length, dimension));
        }

        return vector;
    }

    private void projectToken(String token, float[] vector) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));

            // Use 4-byte slices to hash into dimensions with positive/negative sign
            for (int i = 0; i < hash.length - 3; i += 4) {
                int rawIndex = ((hash[i] & 0xFF) << 24) |
                               ((hash[i + 1] & 0xFF) << 16) |
                               ((hash[i + 2] & 0xFF) << 8) |
                               (hash[i + 3] & 0xFF);

                int index = Math.abs(rawIndex % dimension);
                float sign = (hash[i] % 2 == 0) ? 1.0f : -1.0f;
                float weight = 1.0f + ((hash[i + 1] & 0x0F) / 16.0f);
                vector[index] += sign * weight;
            }
        } catch (NoSuchAlgorithmException e) {
            // Fallback to standard hashCode
            int hash = token.hashCode();
            int index = Math.abs(hash % dimension);
            vector[index] += 1.0f;
        }
    }

    private void normalizeVector(float[] vector) {
        double sumSq = 0.0;
        for (float val : vector) {
            sumSq += val * val;
        }

        if (sumSq > 0.0) {
            float norm = (float) Math.sqrt(sumSq);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    private float[] createZeroVector() {
        return new float[dimension];
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}
