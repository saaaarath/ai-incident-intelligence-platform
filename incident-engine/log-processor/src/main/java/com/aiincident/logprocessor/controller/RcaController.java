package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.rca.LlmRcaEngine;
import com.aiincident.logprocessor.rca.RcaContext;
import com.aiincident.logprocessor.rca.RcaContextBuilder;
import com.aiincident.logprocessor.rca.RcaContextBuilder.RcaContextOptions;
import com.aiincident.logprocessor.rca.RcaReport;
import com.aiincident.logprocessor.rca.RcaValidationResult;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing the AI Root Cause Analysis (RCA) Context, LLM RCA Engine,
 * and Evidence Grounding Validation APIs.
 */
@RestController
@RequestMapping({"/incidents", "/api/incidents"})
public class RcaController {

    private final RcaContextBuilder rcaContextBuilder;
    private final LlmRcaEngine llmRcaEngine;

    @Autowired
    public RcaController(
            RcaContextBuilder rcaContextBuilder,
            @Autowired(required = false) LlmRcaEngine llmRcaEngine) {
        this.rcaContextBuilder = rcaContextBuilder;
        this.llmRcaEngine = llmRcaEngine;
    }

    /**
     * Retrieve complete structured RCA context evidence package for an incident.
     * Example: GET /incidents/1/rca-context?bufferMinutes=5&maxLogs=50&historicalTopK=3&runbookTopK=3
     */
    @GetMapping("/{id}/rca-context")
    public ResponseEntity<RcaContext> getRcaContext(
            @PathVariable String id,
            @RequestParam(defaultValue = "5") Integer bufferMinutes,
            @RequestParam(defaultValue = "50") Integer maxLogs,
            @RequestParam(defaultValue = "3") Integer historicalTopK,
            @RequestParam(defaultValue = "3") Integer runbookTopK) {

        RcaContextOptions options = new RcaContextOptions(
                bufferMinutes != null ? bufferMinutes : 5,
                maxLogs != null ? maxLogs : 50,
                historicalTopK != null ? historicalTopK : 3,
                runbookTopK != null ? runbookTopK : 3
        );

        return rcaContextBuilder.buildContext(id, options)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Trigger and retrieve AI Root Cause Analysis (RCA) report for an incident.
     * Example: POST /incidents/1/rca
     */
    @PostMapping("/{id}/rca")
    public ResponseEntity<RcaReport> generateIncidentRca(
            @PathVariable String id,
            @RequestParam(defaultValue = "5") Integer bufferMinutes,
            @RequestParam(defaultValue = "50") Integer maxLogs,
            @RequestParam(defaultValue = "3") Integer historicalTopK,
            @RequestParam(defaultValue = "3") Integer runbookTopK) {

        if (llmRcaEngine == null) {
            return ResponseEntity.notFound().build();
        }

        RcaContextOptions options = new RcaContextOptions(
                bufferMinutes != null ? bufferMinutes : 5,
                maxLogs != null ? maxLogs : 50,
                historicalTopK != null ? historicalTopK : 3,
                runbookTopK != null ? runbookTopK : 3
        );

        return llmRcaEngine.analyzeIncident(id, options)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Retrieve AI Root Cause Analysis (RCA) report for an incident via GET.
     * Example: GET /incidents/1/rca
     */
    @GetMapping("/{id}/rca")
    public ResponseEntity<RcaReport> getIncidentRca(
            @PathVariable String id,
            @RequestParam(defaultValue = "5") Integer bufferMinutes,
            @RequestParam(defaultValue = "50") Integer maxLogs,
            @RequestParam(defaultValue = "3") Integer historicalTopK,
            @RequestParam(defaultValue = "3") Integer runbookTopK) {

        return generateIncidentRca(id, bufferMinutes, maxLogs, historicalTopK, runbookTopK);
    }

    /**
     * On-demand AI Root Cause Analysis for an explicit RcaContext payload.
     * Example: POST /api/incidents/analyze-rca
     */
    @PostMapping("/analyze-rca")
    public ResponseEntity<RcaReport> analyzeContext(@RequestBody RcaContext context) {
        if (llmRcaEngine == null) {
            return ResponseEntity.notFound().build();
        }
        if (context == null) {
            return ResponseEntity.badRequest().build();
        }

        RcaReport report = llmRcaEngine.analyzeContext(context);
        return ResponseEntity.ok(report);
    }

    /**
     * Validate an arbitrary RCA report against an RcaContext evidence package.
     * Example: POST /api/incidents/validate-rca
     */
    @PostMapping("/validate-rca")
    public ResponseEntity<RcaValidationResult> validateRca(@RequestBody RcaValidationRequest request) {
        if (llmRcaEngine == null) {
            return ResponseEntity.notFound().build();
        }
        if (request == null || request.report() == null) {
            return ResponseEntity.badRequest().body(RcaValidationResult.invalid(
                    RcaValidationResult.RcaValidationStatus.INVALID_SCHEMA,
                    List.of("Request must include a non-null 'report' object"),
                    List.of(),
                    List.of()
            ));
        }

        RcaValidationResult result = llmRcaEngine.validateReport(request.report(), request.context());
        return ResponseEntity.ok(result);
    }

    public record RcaValidationRequest(RcaReport report, RcaContext context) {}
}
