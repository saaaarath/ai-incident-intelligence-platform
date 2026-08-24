package com.aiincident.logprocessor.historical;

/**
 * Categories of historical operational failures and incidents.
 */
public enum HistoricalIncidentCategory {
    DATABASE_CONNECTION_EXHAUSTION,
    DEPLOYMENT_REGRESSION,
    SERVICE_UNAVAILABLE,
    NETWORK_LATENCY,
    MEMORY_PRESSURE,
    CACHE_FAILURE,
    DEPENDENCY_TIMEOUT,
    MESSAGE_PROCESSING_FAILURE;

    public static HistoricalIncidentCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (HistoricalIncidentCategory category : values()) {
            if (category.name().equals(normalized)) {
                return category;
            }
        }
        return null;
    }
}
