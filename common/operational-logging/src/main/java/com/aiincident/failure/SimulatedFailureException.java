package com.aiincident.failure;

public abstract class SimulatedFailureException extends RuntimeException {
    protected SimulatedFailureException(String message) {
        super(message);
    }

    protected SimulatedFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
