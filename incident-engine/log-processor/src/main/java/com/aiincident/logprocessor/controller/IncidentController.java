package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentCorrelationService;
import com.aiincident.logprocessor.incident.IncidentEvidence;
import com.aiincident.logprocessor.incident.IncidentRepository;
import com.aiincident.logprocessor.incident.IncidentService;
import com.aiincident.logprocessor.incident.IncidentStatus;
import com.aiincident.logprocessor.historical.embedding.IncidentRetrievalContext;
import com.aiincident.logprocessor.historical.embedding.IncidentRetrievalService;
import com.aiincident.logprocessor.historical.embedding.SemanticSearchResult;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/incidents", "/api/incidents"})
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentRepository incidentRepository;
    private final IncidentCorrelationService correlationService;
    private final IncidentRetrievalService incidentRetrievalService;

    public IncidentController(
            IncidentService incidentService,
            IncidentRepository incidentRepository) {
        this(incidentService, incidentRepository, null, null);
    }

    @Autowired
    public IncidentController(
            IncidentService incidentService,
            IncidentRepository incidentRepository,
            @Autowired(required = false) IncidentCorrelationService correlationService,
            @Autowired(required = false) IncidentRetrievalService incidentRetrievalService) {
        this.incidentService = incidentService;
        this.incidentRepository = incidentRepository;
        this.correlationService = correlationService;
        this.incidentRetrievalService = incidentRetrievalService;
    }

    /**
     * Query incidents with optional filters: status, severity, service, and time range (from, to).
     * Example: GET /incidents?status=OPEN&severity=CRITICAL&service=order-service
     */
    @GetMapping
    public ResponseEntity<List<Incident>> getIncidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) AnomalySeverity severity,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String fingerprint,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        List<Incident> results = incidentService.findIncidents(status, severity, service, fingerprint, from, to);
        return ResponseEntity.ok(results);
    }

    /**
     * Get incident by ID.
     * Example: GET /incidents/1
     */
    @GetMapping("/{id}")
    public ResponseEntity<Incident> getIncidentById(@PathVariable Long id) {
        return incidentService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Get correlated evidence for a specific incident.
     * Example: GET /incidents/1/evidence
     */
    @GetMapping("/{id}/evidence")
    public ResponseEntity<List<IncidentEvidence>> getIncidentEvidence(@PathVariable Long id) {
        if (incidentService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<IncidentEvidence> evidence = incidentService.getEvidenceByIncidentId(id);
        return ResponseEntity.ok(evidence);
    }

    /**
     * Retrieve historically similar incidents for a current active incident.
     * Example: GET /incidents/1/similar?topK=5
     */
    @GetMapping("/{id}/similar")
    public ResponseEntity<List<SemanticSearchResult>> getSimilarIncidents(
            @PathVariable String id,
            @RequestParam(defaultValue = "5") Integer topK) {
        if (incidentRetrievalService == null) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        if (incidentRetrievalService.resolveIncident(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<SemanticSearchResult> similar = incidentRetrievalService.findSimilarIncidents(id, topK);
        return ResponseEntity.ok(similar);
    }

    /**
     * Retrieve actionable operational runbooks for mitigating a current active incident.
     * Example: GET /incidents/1/runbooks?topK=3
     */
    @GetMapping("/{id}/runbooks")
    public ResponseEntity<List<SemanticSearchResult>> getRelevantRunbooks(
            @PathVariable String id,
            @RequestParam(defaultValue = "3") Integer topK) {
        if (incidentRetrievalService == null) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        if (incidentRetrievalService.resolveIncident(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<SemanticSearchResult> runbooks = incidentRetrievalService.findRelevantRunbooks(id, topK);
        return ResponseEntity.ok(runbooks);
    }

    /**
     * Retrieve full operational knowledge context (similar incidents + runbooks + postmortems) for an incident.
     * Example: GET /incidents/1/context?topK=3
     */
    @GetMapping("/{id}/context")
    public ResponseEntity<IncidentRetrievalContext> getIncidentContext(
            @PathVariable String id,
            @RequestParam(defaultValue = "3") Integer topK) {
        if (incidentRetrievalService == null) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        return incidentRetrievalService.getIncidentRetrievalContext(id, topK)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Trigger incident correlation over a time window.
     * Example: POST /api/incidents/correlate?from=2026-08-23T12:00:00Z&to=2026-08-23T12:15:00Z
     */
    @PostMapping("/correlate")
    public ResponseEntity<List<Incident>> correlateEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        if (correlationService == null) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        List<Incident> correlated = correlationService.correlateTimeRange(from, to);
        return ResponseEntity.ok(correlated);
    }

    /**
     * Acknowledge an incident (transitions from OPEN to INVESTIGATING).
     * Example: POST /incidents/1/acknowledge
     */
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<?> acknowledgeIncident(@PathVariable Long id) {
        try {
            Incident updated = incidentService.acknowledgeIncident(id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(404, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(400, e.getMessage()));
        }
    }

    /**
     * Resolve an incident (transitions to RESOLVED).
     * Example: POST /incidents/1/resolve
     */
    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolveIncident(@PathVariable Long id) {
        try {
            Incident updated = incidentService.resolveIncident(id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(404, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(400, e.getMessage()));
        }
    }

    /**
     * Close an incident (transitions to CLOSED).
     * Example: POST /incidents/1/close
     */
    @PostMapping("/{id}/close")
    public ResponseEntity<?> closeIncident(@PathVariable Long id) {
        try {
            Incident updated = incidentService.closeIncident(id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(404, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(400, e.getMessage()));
        }
    }

    /**
     * Transition the lifecycle status of an incident via status parameter (backward compatibility).
     * Example: PATCH /api/incidents/1/status?status=INVESTIGATING
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateIncidentStatus(
            @PathVariable Long id,
            @RequestParam IncidentStatus status) {
        try {
            Incident updated = incidentService.updateStatus(id, status);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(404, e.getMessage()));
        }
    }

    /**
     * Manually create an incident.
     * Example: POST /incidents
     */
    @PostMapping
    public ResponseEntity<Incident> createIncident(@RequestBody Incident request) {
        Incident incident = new Incident(
                request.getTitle(),
                request.getSeverity() != null ? request.getSeverity() : AnomalySeverity.MEDIUM,
                request.getStatus() != null ? request.getStatus() : IncidentStatus.OPEN,
                request.getPrimaryService(),
                request.getStartedAt() != null ? request.getStartedAt() : Instant.now(),
                request.getDetectedAt() != null ? request.getDetectedAt() : Instant.now(),
                request.getDescription(),
                request.getMetric()
        );
        Incident saved = incidentRepository.save(incident);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    public record ErrorResponse(int status, String error) {}
}
