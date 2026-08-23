package com.aiincident.logging.deployment;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DeploymentEvent(
        String eventId,
        String eventType,
        String service,
        String version,
        Instant timestamp,
        String traceId,
        Map<String, Object> metadata
) {
    public DeploymentEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static DeploymentEvent started(String service, String version, String traceId, Map<String, Object> metadata) {
        return new DeploymentEvent(
                UUID.randomUUID().toString(),
                "DEPLOYMENT_STARTED",
                service,
                version,
                Instant.now(),
                traceId,
                metadata
        );
    }

    public static DeploymentEvent completed(String service, String version, String traceId, Map<String, Object> metadata) {
        return new DeploymentEvent(
                UUID.randomUUID().toString(),
                "DEPLOYMENT_COMPLETED",
                service,
                version,
                Instant.now(),
                traceId,
                metadata
        );
    }

    public static DeploymentEvent create(String eventType, String service, String version, String traceId, Map<String, Object> metadata) {
        return new DeploymentEvent(
                UUID.randomUUID().toString(),
                eventType,
                service,
                version,
                Instant.now(),
                traceId,
                metadata
        );
    }
}
