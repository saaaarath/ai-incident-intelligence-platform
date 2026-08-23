package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.anomaly.AnomalyDetectionService;
import com.aiincident.logprocessor.anomaly.AnomalyEvent;
import com.aiincident.logprocessor.anomaly.AnomalyRepository;
import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/anomalies")
public class AnomalyController {

    private final AnomalyDetectionService anomalyDetectionService;
    private final AnomalyRepository anomalyRepository;

    public AnomalyController(
            AnomalyDetectionService anomalyDetectionService,
            AnomalyRepository anomalyRepository) {
        this.anomalyDetectionService = anomalyDetectionService;
        this.anomalyRepository = anomalyRepository;
    }

    /**
     * Query persisted anomaly events with optional filters.
     * Example: GET /api/anomalies?service=order-service&metric=errorRate
     */
    @GetMapping
    public ResponseEntity<List<AnomalyEvent>> getAnomalies(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String metric,
            @RequestParam(required = false) AnomalySeverity severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        if (service != null && !service.isBlank() && from != null && to != null) {
            return ResponseEntity.ok(anomalyRepository.findByServiceAndDetectedAtBetween(service.trim(), from, to));
        }
        if (service != null && !service.isBlank() && metric != null && !metric.isBlank()) {
            return ResponseEntity.ok(anomalyRepository.findByServiceAndMetric(service.trim(), metric.trim()));
        }
        if (service != null && !service.isBlank()) {
            return ResponseEntity.ok(anomalyRepository.findByService(service.trim()));
        }
        if (metric != null && !metric.isBlank()) {
            return ResponseEntity.ok(anomalyRepository.findByMetric(metric.trim()));
        }
        if (severity != null) {
            return ResponseEntity.ok(anomalyRepository.findBySeverity(severity));
        }
        if (from != null && to != null) {
            return ResponseEntity.ok(anomalyRepository.findByDetectedAtBetween(from, to));
        }

        return ResponseEntity.ok(anomalyRepository.findAll());
    }

    /**
     * Run baseline anomaly detection and persist any detected anomaly events.
     * Example: POST /api/anomalies/detect?currentStart=...&currentEnd=...&baselineStart=...&baselineEnd=...
     */
    @PostMapping("/detect")
    public ResponseEntity<List<AnomalyEvent>> detectAnomalies(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant currentStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant currentEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant baselineStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant baselineEnd) {

        Instant effectiveCurrentEnd = currentEnd != null ? currentEnd : Instant.now();
        Instant effectiveCurrentStart = currentStart != null ? currentStart : effectiveCurrentEnd.minus(Duration.ofMinutes(1));
        Instant effectiveBaselineEnd = baselineEnd != null ? baselineEnd : effectiveCurrentStart;
        Instant effectiveBaselineStart = baselineStart != null ? baselineStart : effectiveBaselineEnd.minus(Duration.ofMinutes(15));

        List<AnomalyEvent> anomalies;
        if (service != null && !service.isBlank()) {
            List<AnomalyEvent> detected = anomalyDetectionService.detectAnomaliesForService(
                    service.trim(),
                    effectiveCurrentStart,
                    effectiveCurrentEnd,
                    effectiveBaselineStart,
                    effectiveBaselineEnd
            );
            anomalies = !detected.isEmpty() ? anomalyRepository.saveAll(detected) : List.of();
        } else {
            anomalies = anomalyDetectionService.detectAndSaveAnomalies(
                    effectiveCurrentStart,
                    effectiveCurrentEnd,
                    effectiveBaselineStart,
                    effectiveBaselineEnd
            );
        }

        return ResponseEntity.ok(anomalies);
    }
}
