package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.fingerprint.ErrorFingerprint;
import com.aiincident.logprocessor.fingerprint.ErrorFingerprintService;
import com.aiincident.logprocessor.repository.LogEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/fingerprints", "/api/fingerprints"})
public class FingerprintController {

    private final ErrorFingerprintService fingerprintService;
    private final LogEventRepository logEventRepository;

    public FingerprintController(
            ErrorFingerprintService fingerprintService,
            LogEventRepository logEventRepository) {
        this.fingerprintService = fingerprintService;
        this.logEventRepository = logEventRepository;
    }

    /**
     * Generate normalized error fingerprint for input parameters.
     * Example: POST /api/fingerprints/generate
     * Body: { "service": "payment-service", "eventType": "DB_TIMEOUT", "message": "DB connection timeout after 3000ms" }
     */
    @PostMapping({"/generate", "/normalize"})
    public ResponseEntity<ErrorFingerprint> generateFingerprint(@RequestBody FingerprintRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }
        ErrorFingerprint fp = fingerprintService.generateFingerprint(
                request.service(),
                request.eventType(),
                request.message()
        );
        return ResponseEntity.ok(fp);
    }

    /**
     * Query distinct error fingerprint summaries and occurrence counts over a time range.
     * Example: GET /api/fingerprints?service=payment-service&from=2026-08-23T12:00:00Z&to=2026-08-23T16:00:00Z
     */
    @GetMapping
    public ResponseEntity<List<ErrorFingerprintService.FingerprintSummary>> getFingerprintSummaries(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        List<ErrorFingerprintService.FingerprintSummary> summaries = fingerprintService.getFingerprintSummaries(from, to, service);
        return ResponseEntity.ok(summaries);
    }

    /**
     * Retrieve log events grouped by their error fingerprints.
     * Example: GET /api/fingerprints/groups
     */
    @GetMapping("/groups")
    public ResponseEntity<Map<String, List<ProcessedLogEvent>>> getGroupedEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        Instant startTime = from != null ? from : Instant.now().minusSeconds(86400);
        Instant endTime = to != null ? to : Instant.now();

        List<ProcessedLogEvent> logs = logEventRepository.findByTimestampBetweenOrderByTimestampAsc(startTime, endTime);
        Map<String, List<ProcessedLogEvent>> grouped = fingerprintService.groupEventsByFingerprint(logs);

        return ResponseEntity.ok(grouped);
    }

    public record FingerprintRequest(String service, String eventType, String message) {}
}
