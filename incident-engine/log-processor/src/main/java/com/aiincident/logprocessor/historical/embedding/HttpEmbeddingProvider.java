package com.aiincident.logprocessor.historical.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP REST client integration for OpenAI, Gemini, and Ollama compatible embedding APIs.
 */
public class HttpEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpEmbeddingProvider.class);

    private final String providerName;
    private final String modelName;
    private final int dimension;
    private final String apiUrl;
    private final String apiKey;
    private final RestClient restClient;

    public HttpEmbeddingProvider(EmbeddingProperties properties) {
        String rawProvider = properties.getProvider() != null ? properties.getProvider().toLowerCase().trim() : "openai";
        this.providerName = rawProvider;

        if ("gemini".equalsIgnoreCase(rawProvider)) {
            this.modelName = properties.getModel() != null && !properties.getModel().startsWith("mock-") ? properties.getModel() : "gemini-embedding-001";
            this.dimension = properties.getDimension() > 0 && properties.getDimension() != 384 ? properties.getDimension() : 768;
            this.apiUrl = properties.getApiUrl() != null && !properties.getApiUrl().contains("openai.com") ? properties.getApiUrl() : "https://generativelanguage.googleapis.com/v1beta";
        } else {
            this.modelName = properties.getModel() != null ? properties.getModel() : "text-embedding-3-small";
            this.dimension = properties.getDimension() > 0 ? properties.getDimension() : 1536;
            this.apiUrl = properties.getApiUrl() != null ? properties.getApiUrl() : "https://api.openai.com/v1/embeddings";
        }

        this.apiKey = properties.getApiKey() != null ? properties.getApiKey() : "";

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 5000;
        factory.setConnectTimeout(Duration.ofMillis(timeout));
        factory.setReadTimeout(Duration.ofMillis(timeout));

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(this.apiUrl)
                .build();
    }

    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimension];
        }

        try {
            if ("gemini".equalsIgnoreCase(providerName)) {
                return callGemini(text);
            } else if ("ollama".equalsIgnoreCase(providerName)) {
                return callOllama(text);
            } else {
                return callOpenAi(text);
            }
        } catch (Exception e) {
            log.error("Failed to generate embedding via provider '{}' (URL: {}): {}", providerName, apiUrl, e.getMessage());
            throw new EmbeddingProviderException("Embedding generation failed for provider " + providerName + ": " + e.getMessage(), e);
        }
    }

    private float[] callGemini(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String modelIdentifier = modelName.startsWith("models/") ? modelName : "models/" + modelName;
        String rawModelName = modelName.startsWith("models/") ? modelName.substring(7) : modelName;

        Map<String, Object> body = Map.of(
                "model", modelIdentifier,
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "outputDimensionality", dimension
        );

        String uri = String.format("/models/%s:embedContent?key=%s", rawModelName, apiKey);

        GeminiEmbedContentResponse response = restClient.post()
                .uri(uri)
                .headers(h -> h.addAll(headers))
                .body(body)
                .retrieve()
                .body(GeminiEmbedContentResponse.class);

        if (response == null || response.embedding == null || response.embedding.values == null || response.embedding.values.isEmpty()) {
            throw new EmbeddingProviderException("Empty response received from Gemini embedding API");
        }

        List<Float> values = response.embedding.values;
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i);
        }

        validateDimension(vector);
        return vector;
    }

    private float[] callOpenAi(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        Map<String, Object> body = Map.of(
                "model", modelName,
                "input", text
        );

        OpenAiEmbeddingResponse response = restClient.post()
                .headers(h -> h.addAll(headers))
                .body(body)
                .retrieve()
                .body(OpenAiEmbeddingResponse.class);

        if (response == null || response.data == null || response.data.isEmpty()) {
            throw new EmbeddingProviderException("Empty response received from OpenAI embedding API");
        }

        List<Float> values = response.data.getFirst().embedding;
        if (values == null || values.isEmpty()) {
            throw new EmbeddingProviderException("Empty embedding vector in response");
        }

        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i);
        }

        validateDimension(vector);
        return vector;
    }

    private float[] callOllama(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", modelName,
                "prompt", text
        );

        OllamaEmbeddingResponse response = restClient.post()
                .headers(h -> h.addAll(headers))
                .body(body)
                .retrieve()
                .body(OllamaEmbeddingResponse.class);

        if (response == null || response.embedding == null || response.embedding.isEmpty()) {
            throw new EmbeddingProviderException("Empty response received from Ollama embedding API");
        }

        float[] vector = new float[response.embedding.size()];
        for (int i = 0; i < response.embedding.size(); i++) {
            vector[i] = response.embedding.get(i);
        }

        validateDimension(vector);
        return vector;
    }

    private void validateDimension(float[] vector) {
        if (vector.length != dimension) {
            log.warn("Embedding dimension mismatch: provider returned {}, expected configured dimension {}", vector.length, dimension);
        }
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiEmbedContentResponse {
        @JsonProperty("embedding")
        public GeminiEmbeddingValues embedding;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiEmbeddingValues {
        @JsonProperty("values")
        public List<Float> values;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAiEmbeddingResponse {
        @JsonProperty("data")
        public List<EmbeddingData> data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingData {
        @JsonProperty("embedding")
        public List<Float> embedding;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OllamaEmbeddingResponse {
        @JsonProperty("embedding")
        public List<Float> embedding;
    }
}
