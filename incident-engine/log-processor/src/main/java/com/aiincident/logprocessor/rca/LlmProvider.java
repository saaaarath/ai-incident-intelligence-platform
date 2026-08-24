package com.aiincident.logprocessor.rca;

/**
 * Interface for invoking Large Language Model completions for RCA.
 */
public interface LlmProvider {

    /**
     * Generate text/JSON completion given a system prompt and a user prompt.
     */
    String generateCompletion(String systemPrompt, String userPrompt);

    /**
     * Get the name of the LLM provider.
     */
    String getProviderName();

    /**
     * Get the model name currently configured.
     */
    String getModelName();
}
