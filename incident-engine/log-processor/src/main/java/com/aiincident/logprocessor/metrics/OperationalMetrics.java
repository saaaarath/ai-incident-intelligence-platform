package com.aiincident.logprocessor.metrics;

import java.time.Instant;

public record OperationalMetrics(
        String service,
        Instant windowStart,
        Instant windowEnd,
        long totalEvents,
        long errorCount,
        double errorRate,
        LatencyMetrics latency) {

    public OperationalMetrics {
        if (service != null) {
            service = service.trim();
        }
        if (totalEvents < 0) {
            totalEvents = 0;
        }
        if (errorCount < 0) {
            errorCount = 0;
        }
    }
}
