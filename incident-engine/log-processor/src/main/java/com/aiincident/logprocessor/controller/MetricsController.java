package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.metrics.MetricsAggregationService;
import com.aiincident.logprocessor.metrics.OperationalMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsAggregationService metricsAggregationService;

    public MetricsController(MetricsAggregationService metricsAggregationService) {
        this.metricsAggregationService = metricsAggregationService;
    }

    /**
     * Get windowed operational metrics.
     * Example: GET /api/metrics?service=order-service&from=2026-08-23T12:00:00Z&to=2026-08-23T13:00:00Z&windowMinutes=1
     */
    @GetMapping
    public ResponseEntity<List<OperationalMetrics>> getMetrics(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer windowMinutes,
            @RequestParam(required = false) Integer windowSeconds) {

        Duration windowDuration = null;
        if (windowMinutes != null && windowMinutes > 0) {
            windowDuration = Duration.ofMinutes(windowMinutes);
        } else if (windowSeconds != null && windowSeconds > 0) {
            windowDuration = Duration.ofSeconds(windowSeconds);
        }

        List<OperationalMetrics> metrics = metricsAggregationService.getMetrics(service, from, to, windowDuration);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get single-window metric summary across a time window.
     * Example: GET /api/metrics/summary?service=order-service&from=2026-08-23T12:00:00Z&to=2026-08-23T13:00:00Z
     */
    @GetMapping("/summary")
    public ResponseEntity<OperationalMetrics> getSummary(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        OperationalMetrics summary = metricsAggregationService.getSummary(service, from, to);
        return ResponseEntity.ok(summary);
    }

    /**
     * Get distinct services with operational events.
     * Example: GET /api/metrics/services
     */
    @GetMapping("/services")
    public ResponseEntity<List<String>> getServices() {
        return ResponseEntity.ok(metricsAggregationService.getServices());
    }
}
