package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentRepository;
import com.aiincident.logprocessor.incident.IncidentService;
import com.aiincident.logprocessor.incident.IncidentStatus;
import java.time.Instant;
import java.util.List;
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
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentRepository incidentRepository;

    public IncidentController(IncidentService incidentService, IncidentRepository incidentRepository) {
        this.incidentService = incidentService;
        this.incidentRepository = incidentRepository;
    }

    /**
     * Query incidents with optional filters.
     * Example: GET /api/incidents?status=OPEN&service=order-service
     */
    @GetMapping
    public ResponseEntity<List<Incident>> getIncidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        if (status != null) {
            return ResponseEntity.ok(incidentService.findByStatus(status));
        }
        if (service != null && !service.isBlank()) {
            return ResponseEntity.ok(incidentService.findByService(service.trim()));
        }
        if (from != null && to != null) {
            return ResponseEntity.ok(incidentRepository.findByDetectedAtBetween(from, to));
        }

        return ResponseEntity.ok(incidentService.findAll());
    }

    /**
     * Get incident by primary ID.
     * Example: GET /api/incidents/1
     */
    @GetMapping("/{id}")
    public ResponseEntity<Incident> getIncidentById(@PathVariable Long id) {
        return incidentService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Transition the lifecycle status of an incident.
     * Example: PATCH /api/incidents/1/status?status=INVESTIGATING
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Incident> updateIncidentStatus(
            @PathVariable Long id,
            @RequestParam IncidentStatus status) {
        try {
            Incident updated = incidentService.updateStatus(id, status);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Manually create an incident.
     * Example: POST /api/incidents
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
}
