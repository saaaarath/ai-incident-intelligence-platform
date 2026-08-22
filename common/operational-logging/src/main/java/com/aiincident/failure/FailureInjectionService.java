package com.aiincident.failure;

import com.aiincident.logging.StructuredLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FailureInjectionService {

    private final boolean globalEnabled;
    private final String securityToken;
    private final String serviceName;
    private final StructuredLogger logger;

    private final AtomicBoolean failureActive = new AtomicBoolean(false);
    private final AtomicReference<FailureType> currentFailureType = new AtomicReference<>(FailureType.NONE);
    private final AtomicLong currentLatencyMs = new AtomicLong(3000L);

    public FailureInjectionService(
            @Value("${failure.injection.enabled:true}") boolean globalEnabled,
            @Value("${failure.injection.security-token:}") String securityToken,
            @Value("${failure.injection.type:NONE}") String initialType,
            @Value("${failure.injection.latency-ms:3000}") long initialLatencyMs,
            @Value("${spring.application.name:unknown-service}") String serviceName,
            ObjectMapper objectMapper) {
        this.globalEnabled = globalEnabled;
        this.securityToken = securityToken == null ? "" : securityToken.trim();
        this.serviceName = serviceName;
        this.logger = new StructuredLogger(LoggerFactory.getLogger(FailureInjectionService.class), objectMapper, serviceName);

        FailureType type = parseFailureType(initialType);
        if (type != FailureType.NONE) {
            enableFailure(type, initialLatencyMs);
        }
    }

    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public boolean isFailureActive() {
        return globalEnabled && failureActive.get() && currentFailureType.get() != FailureType.NONE;
    }

    public FailureConfigResponse getFailureConfig() {
        return new FailureConfigResponse(
                isFailureActive(),
                currentFailureType.get(),
                currentLatencyMs.get());
    }

    public FailureConfigResponse enableFailure(FailureType type, Long latencyMs) {
        if (type == null || type == FailureType.NONE) {
            return disableFailure();
        }
        currentFailureType.set(type);
        if (latencyMs != null && latencyMs > 0) {
            currentLatencyMs.set(latencyMs);
        }
        failureActive.set(true);
        return getFailureConfig();
    }

    public FailureConfigResponse disableFailure() {
        failureActive.set(false);
        currentFailureType.set(FailureType.NONE);
        return getFailureConfig();
    }

    public boolean validateSecurityToken(String token) {
        if (securityToken.isEmpty()) {
            return true;
        }
        return securityToken.equals(token != null ? token.trim() : "");
    }

    public void maybeInjectFailure() {
        if (!isFailureActive()) {
            return;
        }

        FailureType failureType = currentFailureType.get();
        switch (failureType) {
            case LATENCY -> {
                long delayMs = currentLatencyMs.get();
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            case DB_FAILURE -> {
                logger.error("DB_TIMEOUT", "Simulated database connection/query timeout", Map.of("failureType", "DB_FAILURE"), null);
                throw new SimulatedDatabaseException("Simulated database operation failure");
            }
            case SERVICE_UNAVAILABLE -> {
                logger.error("SERVICE_UNAVAILABLE", "Simulated service unavailable failure", Map.of("failureType", "SERVICE_UNAVAILABLE"), null);
                throw new SimulatedServiceUnavailableException("Service is temporarily unavailable (simulated failure)");
            }
            case ERROR_SPIKE -> {
                logger.error("ERROR_SPIKE", "Simulated internal error spike", Map.of("failureType", "ERROR_SPIKE"), null);
                throw new SimulatedErrorSpikeException("Simulated internal error spike");
            }
            default -> {
            }
        }
    }

    private static FailureType parseFailureType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            return FailureType.NONE;
        }
        try {
            return FailureType.valueOf(typeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FailureType.NONE;
        }
    }
}
