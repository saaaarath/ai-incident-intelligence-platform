package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.historical.HistoricalIncident;
import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.HistoricalIncidentService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/historical-incidents", "/api/historical-incidents"})
public class HistoricalIncidentController {

    private final HistoricalIncidentService historicalIncidentService;

    public HistoricalIncidentController(HistoricalIncidentService historicalIncidentService) {
        this.historicalIncidentService = historicalIncidentService;
    }

    /**
     * Query historical operational incidents with optional filtering by category, affected service, or keyword search.
     * Example: GET /api/historical-incidents?category=DATABASE_CONNECTION_EXHAUSTION&service=payment-service
     */
    @GetMapping
    public ResponseEntity<List<HistoricalIncident>> getHistoricalIncidents(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String query) {

        HistoricalIncidentCategory cat = HistoricalIncidentCategory.fromString(category);
        List<HistoricalIncident> results = historicalIncidentService.filter(cat, service, query);
        return ResponseEntity.ok(results);
    }

    /**
     * Retrieve a specific historical incident by numeric ID or unique incidentId code (e.g. HIST-INC-001).
     * Example: GET /api/historical-incidents/1 or GET /api/historical-incidents/HIST-INC-001
     */
    @GetMapping("/{id}")
    public ResponseEntity<HistoricalIncident> getHistoricalIncidentById(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Long numericId = Long.parseLong(id.trim());
            return historicalIncidentService.getById(numericId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> historicalIncidentService.getByIncidentId(id.trim())
                            .map(ResponseEntity::ok)
                            .orElseGet(() -> ResponseEntity.notFound().build()));
        } catch (NumberFormatException e) {
            return historicalIncidentService.getByIncidentId(id.trim())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
    }

    /**
     * List all supported historical incident failure categories.
     * Example: GET /api/historical-incidents/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        List<String> categories = Arrays.stream(HistoricalIncidentCategory.values())
                .map(Enum::name)
                .toList();
        return ResponseEntity.ok(categories);
    }

    /**
     * Trigger canonical dataset seeding into PostgreSQL.
     * Example: POST /api/historical-incidents/seed
     */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedDataset() {
        int seeded = historicalIncidentService.seedDataset();
        long total = historicalIncidentService.count();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "seededCount", seeded,
                "totalCount", total,
                "message", String.format("Historical dataset seeded. Total available records: %d", total)
        ));
    }

    /**
     * Register a new historical incident post-mortem record.
     * Example: POST /api/historical-incidents
     */
    @PostMapping
    public ResponseEntity<HistoricalIncident> createHistoricalIncident(@RequestBody HistoricalIncident incident) {
        if (incident.getTitle() == null || incident.getTitle().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (incident.getCategory() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (incident.getRootCause() == null || incident.getRootCause().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (incident.getResolution() == null || incident.getResolution().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (incident.getPrevention() == null || incident.getPrevention().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (incident.getIncidentId() == null || incident.getIncidentId().isBlank()) {
            incident.setIncidentId("HIST-INC-" + System.currentTimeMillis());
        }

        HistoricalIncident saved = historicalIncidentService.save(incident);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
