package com.aiincident.logprocessor.incident;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic classifier for log and event types.
 * Identifies failure/error events and maps them to severity without AI.
 */
@Component
public class EventTypeClassifier {

    private static final Set<String> CRITICAL_EVENT_PATTERNS = Set.of(
            "DB_TIMEOUT",
            "DATABASE_TIMEOUT",
            "DB_FAILURE",
            "POOL_EXHAUSTED",
            "CONNECTION_POOL_EXHAUSTED",
            "RESOURCE_EXHAUSTED",
            "OUT_OF_MEMORY",
            "SERVICE_UNAVAILABLE",
            "OUTAGE"
    );

    private static final Set<String> HIGH_SEVERITY_PATTERNS = Set.of(
            "PAYMENT_FAILED",
            "PAYMENT_FAILURE",
            "ORDER_TIMEOUT",
            "ORDER_FAILED",
            "INVENTORY_RESERVATION_FAILED",
            "DOWNSTREAM_TIMEOUT",
            "HTTP_500",
            "HTTP_503",
            "HIGH_ERROR_RATE",
            "LATENCY_SPIKE"
    );

    /**
     * Determines whether an incoming log/event represents a failure or operational anomaly.
     */
    public boolean isFailureEvent(String level, String eventType, String message) {
        if (level != null) {
            String lvl = level.trim().toUpperCase();
            if (lvl.equals("ERROR") || lvl.equals("FATAL") || lvl.equals("CRITICAL")) {
                return true;
            }
        }

        if (eventType != null) {
            String type = eventType.trim().toUpperCase();
            for (String crit : CRITICAL_EVENT_PATTERNS) {
                if (type.contains(crit)) {
                    return true;
                }
            }
            for (String high : HIGH_SEVERITY_PATTERNS) {
                if (type.contains(high)) {
                    return true;
                }
            }
            if (type.contains("FAIL") || type.contains("TIMEOUT") || type.contains("ERROR") || type.contains("EXHAUST")) {
                return true;
            }
        }

        if (message != null) {
            String msg = message.toLowerCase();
            if (msg.contains("timeout") || msg.contains("pool exhausted") || msg.contains("connection refused")
                    || msg.contains("failure") || msg.contains("failed") || msg.contains("unavailable")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determine severity level based on event type and log level.
     */
    public AnomalySeverity classifySeverity(String level, String eventType, String message) {
        String type = (eventType != null) ? eventType.trim().toUpperCase() : "";
        String lvl = (level != null) ? level.trim().toUpperCase() : "";

        for (String crit : CRITICAL_EVENT_PATTERNS) {
            if (type.contains(crit)) {
                return AnomalySeverity.CRITICAL;
            }
        }

        for (String high : HIGH_SEVERITY_PATTERNS) {
            if (type.contains(high)) {
                return AnomalySeverity.HIGH;
            }
        }

        if (lvl.equals("FATAL") || lvl.equals("CRITICAL")) {
            return AnomalySeverity.CRITICAL;
        }

        if (lvl.equals("ERROR")) {
            return AnomalySeverity.HIGH;
        }

        if (lvl.equals("WARN")) {
            return AnomalySeverity.MEDIUM;
        }

        return AnomalySeverity.LOW;
    }
}
