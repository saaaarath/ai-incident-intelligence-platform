package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentEvidence;
import com.aiincident.logprocessor.incident.IncidentEvidenceRepository;
import com.aiincident.logprocessor.incident.IncidentService;
import com.aiincident.logprocessor.rca.PrimaryFailureAnalysis;
import com.aiincident.logprocessor.rca.PrimaryFailureAnalyzer;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/incidents", "/api/incidents"})
public class PrimaryFailureController {

    private final PrimaryFailureAnalyzer primaryFailureAnalyzer;
    private final IncidentService incidentService;
    private final IncidentEvidenceRepository evidenceRepository;

    public PrimaryFailureController(
            PrimaryFailureAnalyzer primaryFailureAnalyzer,
            IncidentService incidentService,
            IncidentEvidenceRepository evidenceRepository) {
        this.primaryFailureAnalyzer = primaryFailureAnalyzer;
        this.incidentService = incidentService;
        this.evidenceRepository = evidenceRepository;
    }

    /**
     * Perform primary failure vs downstream symptoms RCA analysis for a specific incident.
     * Example: GET /incidents/1/primary-failure
     */
    @GetMapping("/{id}/primary-failure")
    public ResponseEntity<PrimaryFailureAnalysis> getIncidentPrimaryFailure(@PathVariable Long id) {
        return incidentService.findById(id)
                .map(incident -> {
                    PrimaryFailureAnalysis analysis = primaryFailureAnalyzer.analyzeIncident(incident);
                    return ResponseEntity.ok(analysis);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * On-demand primary failure analysis for an explicit list of evidence items.
     * Example: POST /api/incidents/analyze-primary-failure
     */
    @PostMapping("/analyze-primary-failure")
    public ResponseEntity<PrimaryFailureAnalysis> analyzeEvidence(@RequestBody List<IncidentEvidence> evidenceList) {
        PrimaryFailureAnalysis analysis = primaryFailureAnalyzer.analyzeEvidence(null, evidenceList, "unknown");
        return ResponseEntity.ok(analysis);
    }
}
