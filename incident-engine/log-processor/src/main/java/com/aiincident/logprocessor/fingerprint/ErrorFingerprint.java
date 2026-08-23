package com.aiincident.logprocessor.fingerprint;

/**
 * Immutable model representing a normalized error fingerprint.
 */
public record ErrorFingerprint(
        String fingerprintHash,
        String service,
        String eventType,
        String normalizedMessage,
        String canonicalPattern
) {
    public static ErrorFingerprint of(String hash, String service, String eventType, String normalizedMessage) {
        String canonical = String.format("%s:%s:%s",
                service != null ? service.toLowerCase().trim() : "unknown",
                eventType != null ? eventType.toUpperCase().trim() : "UNKNOWN",
                normalizedMessage != null ? normalizedMessage.trim() : "");
        return new ErrorFingerprint(hash, service, eventType, normalizedMessage, canonical);
    }
}
