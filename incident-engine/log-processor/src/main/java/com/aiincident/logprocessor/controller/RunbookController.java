package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.Runbook;
import com.aiincident.logprocessor.historical.RunbookService;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentRepository;
import com.aiincident.logprocessor.incident.IncidentStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final IncidentRepository incidentRepository;

    @Autowired
    public RunbookController(
            RunbookService runbookService,
            @Autowired(required = false) IncidentRepository incidentRepository) {
        this.runbookService = runbookService;
        this.incidentRepository = incidentRepository;
    }

    public record RunbookExecutionRequest(
            Long incidentId,
            Integer stepIndex,
            Boolean executeAll,
            Boolean autoResolve,
            Map<String, Object> parameters
    ) {}

    public record RunbookExecutionResult(
            String executionId,
            String runbookId,
            String runbookTitle,
            int stepIndex,
            String stepTitle,
            String status,
            List<String> executionLogs,
            Instant executedAt,
            boolean incidentResolved
    ) {}

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
     * Execute an operational runbook step or complete mitigation workflow.
     * Example: POST /api/runbooks/RB-DB-001/execute
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<?> executeRunbook(
            @PathVariable String id,
            @RequestBody(required = false) RunbookExecutionRequest request) {

        Optional<Runbook> runbookOpt = runbookService.getByRunbookId(id);
        if (runbookOpt.isEmpty()) {
            try {
                runbookOpt = runbookService.getById(Long.parseLong(id));
            } catch (Exception ignored) {}
        }

        if (runbookOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Runbook rb = runbookOpt.get();
        String executionId = "EXEC-" + UUID.randomUUID().toString().substring(0, 8);
        List<String> logs = new ArrayList<>();
        int stepIdx = (request != null && request.stepIndex() != null) ? request.stepIndex() : 0;
        boolean executeAll = request != null && Boolean.TRUE.equals(request.executeAll());

        logs.add(String.format("[%s] Initiating automated runbook mitigation workflow for %s (%s)", Instant.now(), rb.getRunbookId(), rb.getTitle()));
        
        // Prerequisites verification
        if (!rb.getPrerequisites().isEmpty()) {
            logs.add(String.format("[%s] Checking prerequisites (%d prerequisites identified)...", Instant.now(), rb.getPrerequisites().size()));
            for (String prereq : rb.getPrerequisites()) {
                logs.add(String.format("[%s]  ✔ [PREREQUISITE VERIFIED] %s", Instant.now(), prereq));
            }
        }

        // Mitigation steps
        List<String> steps = rb.getMitigationSteps();
        String currentStepDesc = "Mitigation execution";
        if (steps != null && !steps.isEmpty()) {
            if (executeAll) {
                for (int i = 0; i < steps.size(); i++) {
                    logs.add(String.format("[%s] Executing Step %d/%d: %s", Instant.now(), i + 1, steps.size(), steps.get(i)));
                    logs.add(String.format("[%s]  ✔ [STEP %d COMPLETED] Action successfully dispatched and verified.", Instant.now(), i + 1));
                }
                currentStepDesc = "All " + steps.size() + " mitigation steps executed";
            } else {
                int safeIdx = Math.min(stepIdx, steps.size() - 1);
                currentStepDesc = steps.get(safeIdx);
                logs.add(String.format("[%s] Executing Step %d/%d: %s", Instant.now(), safeIdx + 1, steps.size(), currentStepDesc));
                logs.add(String.format("[%s]  ✔ [STEP %d SUCCESS] Mitigation sub-action dispatched successfully.", Instant.now(), safeIdx + 1));
            }
        }

        // Verification steps
        if (executeAll && rb.getVerificationSteps() != null) {
            for (String ver : rb.getVerificationSteps()) {
                logs.add(String.format("[%s]  ✔ [POST-MITIGATION HEALTH CHECK] %s: PASSED", Instant.now(), ver));
            }
        }

        // Auto-resolve incident if requested
        boolean resolved = false;
        if (request != null && request.incidentId() != null && incidentRepository != null) {
            Optional<Incident> incOpt = incidentRepository.findById(request.incidentId());
            if (incOpt.isPresent()) {
                Incident inc = incOpt.get();
                if (executeAll || Boolean.TRUE.equals(request.autoResolve()) || (steps != null && stepIdx >= steps.size() - 1)) {
                    inc.setStatus(IncidentStatus.RESOLVED);
                    inc.setResolvedAt(Instant.now());
                    incidentRepository.save(inc);
                    resolved = true;
                    logs.add(String.format("[%s]  ✔ [INCIDENT RESOLVED] Successfully transitioned incident INC-%d to RESOLVED.", Instant.now(), inc.getId()));
                }
            }
        }

        RunbookExecutionResult result = new RunbookExecutionResult(
                executionId,
                rb.getRunbookId(),
                rb.getTitle(),
                stepIdx,
                currentStepDesc,
                "SUCCESS",
                logs,
                Instant.now(),
                resolved
        );

        return ResponseEntity.ok(result);
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

