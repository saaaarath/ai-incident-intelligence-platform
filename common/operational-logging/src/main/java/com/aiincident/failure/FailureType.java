package com.aiincident.failure;

public enum FailureType {
    NONE,
    DB_FAILURE,
    LATENCY,
    SERVICE_UNAVAILABLE,
    ERROR_SPIKE
}
