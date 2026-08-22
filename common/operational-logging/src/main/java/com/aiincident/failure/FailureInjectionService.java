package com.aiincident.failure;

import com.aiincident.logging.StructuredLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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

    /** Listeners notified on failure state changes. Thread-safe for concurrent registration. */
    private final List<FailureLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

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

    /** Register a listener to be notified when failure injection state changes. */
    public void addLifecycleListener(FailureLifecycleListener listener) {
        if (listener != null) {
            lifecycleListeners.add(listener);
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
        notifyEnabled(type);
        return getFailureConfig();
    }

    public FailureConfigResponse disableFailure() {
        failureActive.set(false);
        currentFailureType.set(FailureType.NONE);
        notifyDisabled();
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
                logger.info("LATENCY_INJECTED", "Simulated latency injected", Map.of("failureType", "LATENCY", "latencyMs", delayMs));
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
            case CONNECTION_POOL_EXHAUSTED -> {
                // CONNECTION_POOL_EXHAUSTED is handled by the service layer via
                // ConnectionPoolExhaustionSimulator — the filter does not block the request
                // so that the realistic failure path through JPA is exercised.
            }
            default -> {
            }
        }
    }

    // ---- private helpers ----

    private void notifyEnabled(FailureType type) {
        for (FailureLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onFailureEnabled(type);
            } catch (Exception ignored) {
                // Listener errors must not destabilise the control path.
            }
        }
    }

    private void notifyDisabled() {
        for (FailureLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onFailureDisabled();
            } catch (Exception ignored) {
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
