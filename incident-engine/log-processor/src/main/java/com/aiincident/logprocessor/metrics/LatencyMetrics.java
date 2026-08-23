package com.aiincident.logprocessor.metrics;

public record LatencyMetrics(
        long count,
        Double min,
        Double max,
        Double avg,
        Double p50,
        Double p95,
        Double p99) {

    public static LatencyMetrics empty() {
        return new LatencyMetrics(0, null, null, null, null, null, null);
    }
}
