package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.service.LogProcessorService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
public class LogQueryController {

    private final LogProcessorService logProcessorService;

    public LogQueryController(LogProcessorService logProcessorService) {
        this.logProcessorService = logProcessorService;
    }

    @GetMapping
    public ResponseEntity<List<ProcessedLogEvent>> getLogs(
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String eventType) {
        if (traceId != null && !traceId.isBlank()) {
            return ResponseEntity.ok(logProcessorService.findByTraceId(traceId.trim()));
        }
        if (service != null && !service.isBlank()) {
            return ResponseEntity.ok(logProcessorService.findByService(service.trim()));
        }
        if (eventType != null && !eventType.isBlank()) {
            return ResponseEntity.ok(logProcessorService.findByEventType(eventType.trim()));
        }
        return ResponseEntity.ok(logProcessorService.findAllLogs());
    }

    @GetMapping("/trace/{traceId}")
    public ResponseEntity<List<ProcessedLogEvent>> getLogsByTraceId(@PathVariable String traceId) {
        return ResponseEntity.ok(logProcessorService.findByTraceId(traceId));
    }
}
