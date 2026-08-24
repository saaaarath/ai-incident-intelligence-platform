package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.Runbook;
import com.aiincident.logprocessor.historical.RunbookService;
import java.util.List;
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
@RequestMapping({"/runbooks", "/api/runbooks"})
public class RunbookController {

    private final RunbookService runbookService;

    public RunbookController(RunbookService runbookService) {
        this.runbookService = runbookService;
    }

    /**
     * Query operational runbooks with optional filtering by category, applicable service, or keyword search.
     * Example: GET /api/runbooks?category=DATABASE_CONNECTION_EXHAUSTION
     */
    @GetMapping
    public ResponseEntity<List<Runbook>> getRunbooks(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String query) {

        HistoricalIncidentCategory cat = HistoricalIncidentCategory.fromString(category);
        List<Runbook> results = runbookService.filter(cat, service, query);
        return ResponseEntity.ok(results);
    }

    /**
     * Retrieve a specific runbook by numeric ID or code (e.g. RB-DB-001).
     * Example: GET /api/runbooks/1 or GET /api/runbooks/RB-DB-001
     */
    @GetMapping("/{id}")
    public ResponseEntity<Runbook> getRunbookById(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Long numericId = Long.parseLong(id.trim());
            return runbookService.getById(numericId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> runbookService.getByRunbookId(id.trim())
                            .map(ResponseEntity::ok)
                            .orElseGet(() -> ResponseEntity.notFound().build()));
        } catch (NumberFormatException e) {
            return runbookService.getByRunbookId(id.trim())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
    }

    /**
     * Register a new operational runbook.
     * Example: POST /api/runbooks
     */
    @PostMapping
    public ResponseEntity<Runbook> createRunbook(@RequestBody Runbook runbook) {
        if (runbook.getTitle() == null || runbook.getTitle().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (runbook.getCategory() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (runbook.getEscalationPath() == null || runbook.getEscalationPath().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (runbook.getRunbookId() == null || runbook.getRunbookId().isBlank()) {
            runbook.setRunbookId("RB-CUSTOM-" + System.currentTimeMillis());
        }

        if (runbook.getContent() == null || runbook.getContent().isBlank()) {
            runbook.setContent(runbook.generateMarkdownContent());
        }

        Runbook saved = runbookService.save(runbook);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
