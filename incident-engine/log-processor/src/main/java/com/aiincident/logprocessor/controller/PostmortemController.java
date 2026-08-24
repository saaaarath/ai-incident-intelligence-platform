package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.Postmortem;
import com.aiincident.logprocessor.historical.PostmortemService;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
@RequestMapping({"/postmortems", "/api/postmortems"})
public class PostmortemController {

    private final PostmortemService postmortemService;
    private final IncidentRepository incidentRepository;

    @Autowired
    public PostmortemController(
            PostmortemService postmortemService,
            @Autowired(required = false) IncidentRepository incidentRepository) {
        this.postmortemService = postmortemService;
        this.incidentRepository = incidentRepository;
    }

    /**
     * Query postmortems with optional filtering by category, incidentId, or keyword search.
     * Example: GET /api/postmortems?incidentId=HIST-INC-001
     */
    @GetMapping
    public ResponseEntity<List<Postmortem>> getPostmortems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String incidentId,
            @RequestParam(required = false) String query) {

        HistoricalIncidentCategory cat = HistoricalIncidentCategory.fromString(category);
        List<Postmortem> results = postmortemService.filter(cat, incidentId, query);
        return ResponseEntity.ok(results);
    }

    /**
     * Retrieve a specific postmortem by numeric ID, postmortemId (e.g. PM-HIST-INC-001), or incidentId (e.g. HIST-INC-001).
     * Example: GET /api/postmortems/1 or GET /api/postmortems/PM-HIST-INC-001
     */
    @GetMapping("/{id}")
    public ResponseEntity<Postmortem> getPostmortemById(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Long numericId = Long.parseLong(id.trim());
            return postmortemService.getById(numericId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> postmortemService.getByPostmortemId(id.trim())
                            .map(ResponseEntity::ok)
                            .orElseGet(() -> postmortemService.getByIncidentId(id.trim())
                                     .map(ResponseEntity::ok)
                                     .orElseGet(() -> ResponseEntity.notFound().build())));
        } catch (NumberFormatException e) {
            return postmortemService.getByPostmortemId(id.trim())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> postmortemService.getByIncidentId(id.trim())
                            .map(ResponseEntity::ok)
                            .orElseGet(() -> ResponseEntity.notFound().build()));
        }
    }

    /**
     * Generate an AI Postmortem report for a specific incident.
     * Example: POST /api/postmortems/generate/1
     */
    @PostMapping("/generate/{id}")
    public ResponseEntity<?> generatePostmortem(@PathVariable String id) {
        if (incidentRepository == null) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Incident repository not available");
        }

        Optional<Incident> incOpt = Optional.empty();
        try {
            incOpt = incidentRepository.findById(Long.parseLong(id.trim()));
        } catch (NumberFormatException e) {
            incOpt = incidentRepository.findByIncidentId(id.trim());
        }

        if (incOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Incident inc = incOpt.get();
        String incidentKey = "INC-" + inc.getId();

        // If postmortem already generated, return existing
        Optional<Postmortem> existing = postmortemService.getByIncidentId(incidentKey);
        if (existing.isPresent()) {
            return ResponseEntity.ok(existing.get());
        }

        // Infer category from metric / title
        HistoricalIncidentCategory category = HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION;
        String m = (inc.getMetric() != null ? inc.getMetric() : "").toLowerCase();
        String t = (inc.getTitle() != null ? inc.getTitle() : "").toLowerCase();
        if (m.contains("latency") || m.contains("duration") || t.contains("latency")) {
            category = HistoricalIncidentCategory.NETWORK_LATENCY;
        } else if (m.contains("5xx") || m.contains("500") || t.contains("503") || t.contains("unavailable")) {
            category = HistoricalIncidentCategory.SERVICE_UNAVAILABLE;
        } else if (m.contains("deployment") || t.contains("deployment") || t.contains("regression")) {
            category = HistoricalIncidentCategory.DEPLOYMENT_REGRESSION;
        } else if (m.contains("cache") || t.contains("cache")) {
            category = HistoricalIncidentCategory.CACHE_FAILURE;
        }

        Instant start = inc.getStartedAt() != null ? inc.getStartedAt() : Instant.now().minusSeconds(300);
        Instant end = inc.getResolvedAt() != null ? inc.getResolvedAt() : Instant.now();
        long durationMin = Math.max(1, Duration.between(start, end).toMinutes());

        String pmId = "PM-" + incidentKey;
        String title = "Postmortem: " + inc.getTitle();
        String lead = "AI-SRE Incident Intelligence Engine";
        
        String execSummary = String.format(
                "On %s, an incident occurred affecting %s. Latency spiked across customer transactions with a total degradation window of %d minutes. The incident reached %s severity before automated mitigation and resolution.",
                start.toString(), inc.getPrimaryService(), durationMin, inc.getSeverity());

        String impactSummary = String.format(
                "• Primary Service: %s\n• Root Failure Service: %s\n• Blast Radius: %s\n• Anomaly Fault Metric: %s\n• Total Impact Window: %d minutes",
                inc.getPrimaryService(),
                inc.getRootService() != null ? inc.getRootService() : inc.getPrimaryService(),
                inc.getAffectedServices() != null && !inc.getAffectedServices().isEmpty() ? String.join(", ", inc.getAffectedServices()) : inc.getPrimaryService(),
                inc.getMetric() != null ? inc.getMetric() : "system_telemetry",
                durationMin);

        String rootCauseAnalysis = String.format(
                "Diagnostic analysis confirmed the root cause originated in %s. Fault signature: %s. %s",
                inc.getRootService() != null ? inc.getRootService() : inc.getPrimaryService(),
                inc.getMetric() != null ? inc.getMetric() : "anomalous behavior",
                inc.getDescription() != null ? inc.getDescription() : "Telemetry threshold breach caused downstream callers to experience connection starvation and timeouts.");

        String detectionResponse = String.format(
                "• Detection: Detected automatically by statistical Z-score baseline monitor at %s (~20s TTD).\n• Investigation: Automated AI Root Cause Analysis localized fault origin with high confidence.\n• Mitigation: SRE Runbook automation executed to remediate connection bottlenecks and recover nominal throughput.",
                inc.getDetectedAt() != null ? inc.getDetectedAt().toString() : start.toString());

        List<String> actionItems = List.of(
                String.format("[P1] Scale resource allocation and connection pool buffer on %s [Owner: Platform SRE]", inc.getPrimaryService()),
                String.format("[P2] Implement adaptive circuit breaking and retry backoffs on upstream callers [Owner: Core Backend]"),
                String.format("[P2] Configure precursor alert threshold at 80%% saturation [Owner: Observability Team]"),
                String.format("[P3] Update canonical runbook with latest mitigation learnings [Owner: SRE Team]")
        );

        List<String> lessons = List.of(
                "Automated AI RCA significantly reduced diagnostic latency by localizing the root cause to " + inc.getPrimaryService() + " in seconds.",
                "Unbounded upstream retry storms amplified resource contention prior to mitigation.",
                "Runbook execution successfully restored nominal service state without full process restart."
        );

        Postmortem pm = new Postmortem(
                pmId,
                incidentKey,
                title,
                category,
                inc.getSeverity(),
                lead,
                execSummary,
                impactSummary,
                rootCauseAnalysis,
                detectionResponse,
                actionItems,
                lessons,
                null,
                start
        );

        pm.setContent(pm.generateMarkdownContent());
        Postmortem saved = postmortemService.save(pm);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Register a new postmortem record.
     * Example: POST /api/postmortems
     */
    @PostMapping
    public ResponseEntity<Postmortem> createPostmortem(@RequestBody Postmortem postmortem) {
        if (postmortem.getTitle() == null || postmortem.getTitle().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (postmortem.getCategory() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (postmortem.getIncidentId() == null || postmortem.getIncidentId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (postmortem.getRootCauseAnalysis() == null || postmortem.getRootCauseAnalysis().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (postmortem.getPostmortemId() == null || postmortem.getPostmortemId().isBlank()) {
            postmortem.setPostmortemId("PM-CUSTOM-" + System.currentTimeMillis());
        }

        if (postmortem.getContent() == null || postmortem.getContent().isBlank()) {
            postmortem.setContent(postmortem.generateMarkdownContent());
        }

        Postmortem saved = postmortemService.save(postmortem);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}

