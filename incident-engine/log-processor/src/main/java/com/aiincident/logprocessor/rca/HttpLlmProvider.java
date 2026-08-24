package com.aiincident.logprocessor.rca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP REST Client for Google Gemini, OpenAI, Ollama, and OpenAI-compatible Chat Completion APIs.
 */
public class HttpLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpLlmProvider.class);

    private final String providerName;
    private final String modelName;
    private final String apiUrl;
    private final String apiKey;
    private final double temperature;
    private final int maxTokens;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpLlmProvider(LlmProperties properties) {
        String rawProvider = properties.getProvider() != null ? properties.getProvider().toLowerCase().trim() : "openai";
        this.providerName = rawProvider;
        this.apiKey = properties.getApiKey() != null ? properties.getApiKey().trim() : "";
        this.temperature = properties.getTemperature();
        this.maxTokens = properties.getMaxTokens() > 0 ? properties.getMaxTokens() : 2048;
        this.objectMapper = new ObjectMapper();

        if ("gemini".equalsIgnoreCase(rawProvider)) {
            this.modelName = (properties.getModel() != null && !properties.getModel().startsWith("mock-"))
                    ? properties.getModel()
                    : "gemini-3.6-flash";
            this.apiUrl = properties.getApiUrl() != null && !properties.getApiUrl().contains("api.openai.com")
                    ? properties.getApiUrl()
                    : "https://generativelanguage.googleapis.com/v1beta";
        } else {
            this.modelName = (properties.getModel() != null && !properties.getModel().startsWith("mock-"))
                    ? properties.getModel()
                    : "gpt-4o-mini";
            this.apiUrl = properties.getApiUrl() != null ? properties.getApiUrl() : "https://api.openai.com/v1/chat/completions";
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 20000;
        factory.setConnectTimeout(Duration.ofMillis(timeout));
        factory.setReadTimeout(Duration.ofMillis(timeout));

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(this.apiUrl)
                .build();
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public String generateCompletion(String systemPrompt, String userPrompt) {
        try {
            if ("gemini".equalsIgnoreCase(providerName)) {
                return callGemini(systemPrompt, userPrompt);
            } else {
                return callOpenAiCompatible(systemPrompt, userPrompt);
            }
        } catch (Exception e) {
            log.error("Failed to invoke LLM provider '{}' (model='{}'): {}", providerName, modelName, e.getMessage(), e);
            throw new RuntimeException("LLM provider invocation failed: " + e.getMessage(), e);
        }
    }

    private String callGemini(String systemPrompt, String userPrompt) {
        String cleanModel = modelName.startsWith("models/") ? modelName.substring(7) : modelName;
        String uri = String.format("/models/%s:generateContent?key=%s", cleanModel, apiKey);

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt != null ? systemPrompt : ""))
                ),
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", userPrompt != null ? userPrompt : ""))
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "maxOutputTokens", maxTokens,
                        "responseMimeType", "application/json"
                )
        );

        log.debug("Invoking Gemini REST API endpoint: /models/{}:generateContent", cleanModel);

        byte[] rawBytes = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.ALL)
                .body(body)
                .retrieve()
                .body(byte[].class);

        String rawResponse = (rawBytes != null && rawBytes.length > 0)
                ? new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8)
                : "";

        if (rawResponse != null && !rawResponse.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(rawResponse);
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    JsonNode parts = candidates.get(0).path("content").path("parts");
                    if (parts.isArray() && parts.size() > 0) {
                        return parts.get(0).path("text").asText();
                    }
                }
            } catch (Exception e) {
                log.warn("Could not parse Gemini envelope, returning raw response: {}", e.getMessage());
            }
        }

        return rawResponse != null ? rawResponse : "{}";
    }

    private String callOpenAiCompatible(String systemPrompt, String userPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new ChatMessage("system", systemPrompt));
        }
        messages.add(new ChatMessage("user", userPrompt != null ? userPrompt : ""));

        ChatCompletionRequest request = new ChatCompletionRequest(
                this.modelName,
                messages,
                this.temperature,
                this.maxTokens
        );

        RestClient.RequestBodySpec spec = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);

        if (apiKey != null && !apiKey.isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }

        ChatCompletionResponse response = spec.body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response != null && response.choices != null && !response.choices.isEmpty()) {
            String content = response.choices.get(0).message.content;
            log.debug("Received OpenAI-compatible completion response (length={})", content != null ? content.length() : 0);
            return content;
        }

        log.warn("Empty response from LLM provider {}", providerName);
        return "{}";
    }

    public record ChatMessage(String role, String content) {}

    public record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            double temperature,
            @JsonProperty("max_tokens") int maxTokens
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatCompletionResponse {
        public List<Choice> choices;

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Choice {
            public Message message;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Message {
            public String role;
            public String content;
        }
    }
}
