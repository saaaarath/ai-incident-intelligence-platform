package com.aiincident.failure;

public record FailureRequest(
        FailureType type,
        Boolean enabled,
        Long latencyMs) {
}
