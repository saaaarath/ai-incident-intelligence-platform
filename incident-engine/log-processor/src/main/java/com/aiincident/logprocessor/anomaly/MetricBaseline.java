package com.aiincident.logprocessor.anomaly;

public record MetricBaseline(
        String metric,
        String service,
        double mean,
        double variability,
        double min,
        double max,
        int sampleCount) {

    public static MetricBaseline empty(String metric, String service) {
        return new MetricBaseline(metric, service, 0.0, 0.0, 0.0, 0.0, 0);
    }
}
