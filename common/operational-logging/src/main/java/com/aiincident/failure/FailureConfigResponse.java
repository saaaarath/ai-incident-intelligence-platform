package com.aiincident.failure;

public record FailureConfigResponse(
        boolean enabled,
        FailureType type,
        long latencyMs) {
}
