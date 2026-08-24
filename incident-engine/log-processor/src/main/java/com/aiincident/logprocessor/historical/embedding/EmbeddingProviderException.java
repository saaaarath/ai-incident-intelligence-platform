package com.aiincident.logprocessor.historical.embedding;

/**
 * Exception thrown when embedding generation encounters an unrecoverable provider or network error.
 */
public class EmbeddingProviderException extends RuntimeException {

    public EmbeddingProviderException(String message) {
        super(message);
    }

    public EmbeddingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
