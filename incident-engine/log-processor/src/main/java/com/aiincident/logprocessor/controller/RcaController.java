package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.rca.RcaContext;
import com.aiincident.logprocessor.rca.RcaContextBuilder;
import com.aiincident.logprocessor.rca.RcaContextBuilder.RcaContextOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing the AI Root Cause Analysis (RCA) Context Builder API.
 */
@RestController
@RequestMapping({"/incidents", "/api/incidents"})
public class RcaController {

    private final RcaContextBuilder rcaContextBuilder;

    public RcaController(RcaContextBuilder rcaContextBuilder) {
        this.rcaContextBuilder = rcaContextBuilder;
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
}
