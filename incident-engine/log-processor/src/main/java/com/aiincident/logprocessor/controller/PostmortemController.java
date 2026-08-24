package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.Postmortem;
import com.aiincident.logprocessor.historical.PostmortemService;
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
@RequestMapping({"/postmortems", "/api/postmortems"})
public class PostmortemController {

    private final PostmortemService postmortemService;

    public PostmortemController(PostmortemService postmortemService) {
        this.postmortemService = postmortemService;
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
