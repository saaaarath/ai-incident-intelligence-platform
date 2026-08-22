package com.aiincident.failure;

public class SimulatedServiceUnavailableException extends SimulatedFailureException {
    public SimulatedServiceUnavailableException(String message) {
        super(message);
    }
}
