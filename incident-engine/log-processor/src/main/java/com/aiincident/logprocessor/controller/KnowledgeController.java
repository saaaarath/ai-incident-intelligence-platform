package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.KnowledgeDocument;
import com.aiincident.logprocessor.historical.KnowledgeDocumentService;
import com.aiincident.logprocessor.historical.KnowledgeDocumentType;
import com.aiincident.logprocessor.historical.embedding.SemanticRetrievalRequest;
import com.aiincident.logprocessor.historical.embedding.SemanticRetrievalService;
import com.aiincident.logprocessor.historical.embedding.SemanticSearchResult;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/knowledge", "/api/knowledge"})
public class KnowledgeController {

    private final KnowledgeDocumentService knowledgeService;
    private final SemanticRetrievalService semanticRetrievalService;

    public KnowledgeController(
            KnowledgeDocumentService knowledgeService,
            SemanticRetrievalService semanticRetrievalService) {
        this.knowledgeService = knowledgeService;
        this.semanticRetrievalService = semanticRetrievalService;
    }

    /**
     * Query all structured operational knowledge documents across historical incidents, postmortems, and runbooks.
     * Example: GET /api/knowledge?query=HikariCP&type=RUNBOOK&service=payment-service
     */
    @GetMapping
    public ResponseEntity<List<KnowledgeDocument>> getKnowledgeDocuments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String service) {

        KnowledgeDocumentType docType = KnowledgeDocumentType.fromString(type);
        HistoricalIncidentCategory cat = HistoricalIncidentCategory.fromString(category);

        List<KnowledgeDocument> results = knowledgeService.search(query, docType, cat, service);
        return ResponseEntity.ok(results);
    }

    /**
     * Retrieve a specific knowledge document by its canonical document ID.
     * Example: GET /api/knowledge/INC:HIST-INC-001, GET /api/knowledge/RB:RB-DB-001, GET /api/knowledge/PM:PM-HIST-INC-001
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<KnowledgeDocument> getDocumentById(@PathVariable String documentId) {
        return knowledgeService.getDocumentById(documentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Semantic similarity search over operational knowledge via JSON request body.
     * Example: POST /api/knowledge/retrieve
     * Body: { "query": "database connection pool timeout", "topK": 5, "type": "RUNBOOK" }
     */
    @PostMapping("/retrieve")
    public ResponseEntity<List<SemanticSearchResult>> retrieveSemantically(
            @RequestBody SemanticRetrievalRequest request) {
        List<SemanticSearchResult> results = semanticRetrievalService.search(request);
        return ResponseEntity.ok(results);
    }

    /**
     * Semantic similarity search over operational knowledge via query parameters.
     * Example: GET /api/knowledge/retrieve?query=database connection pool timeout&topK=5&type=RUNBOOK
     */
    @GetMapping("/retrieve")
    public ResponseEntity<List<SemanticSearchResult>> retrieveSemanticallyGet(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") Integer topK,
            @RequestParam(required = false) Double minScore,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category) {

        SemanticRetrievalRequest request = new SemanticRetrievalRequest(query, topK);
        if (minScore != null) {
            request.setMinScore(minScore);
        }
        request.setType(KnowledgeDocumentType.fromString(type));
        request.setCategory(HistoricalIncidentCategory.fromString(category));

        List<SemanticSearchResult> results = semanticRetrievalService.search(request);
        return ResponseEntity.ok(results);
    }

    /**
     * Dedicated endpoint to find historically similar incidents for an incident description.
     * Example: GET /api/knowledge/similar-incidents?description=HikariCP timeout in payment-service&topK=3
     */
    @GetMapping("/similar-incidents")
    public ResponseEntity<List<SemanticSearchResult>> findSimilarIncidents(
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(defaultValue = "5") Integer topK) {

        String text = description != null && !description.isBlank() ? description : query;
        List<SemanticSearchResult> results = semanticRetrievalService.findSimilarIncidents(text, topK);
        return ResponseEntity.ok(results);
    }

    /**
     * Dedicated endpoint to find relevant operational runbooks for an incident description.
     * Example: GET /api/knowledge/relevant-runbooks?description=HikariCP timeout in payment-service&topK=3
     */
    @GetMapping("/relevant-runbooks")
    public ResponseEntity<List<SemanticSearchResult>> findRelevantRunbooks(
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(defaultValue = "5") Integer topK) {

        String text = description != null && !description.isBlank() ? description : query;
        List<SemanticSearchResult> results = semanticRetrievalService.findRelevantRunbooks(text, topK);
        return ResponseEntity.ok(results);
    }

    /**
     * Dedicated endpoint to find relevant postmortems for an incident description.
     * Example: GET /api/knowledge/relevant-postmortems?description=HikariCP timeout in payment-service&topK=3
     */
    @GetMapping("/relevant-postmortems")
    public ResponseEntity<List<SemanticSearchResult>> findRelevantPostmortems(
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(defaultValue = "5") Integer topK) {

        String text = description != null && !description.isBlank() ? description : query;
        List<SemanticSearchResult> results = semanticRetrievalService.findRelevantPostmortems(text, topK);
        return ResponseEntity.ok(results);
    }

    /**
     * Trigger full operational knowledge database re-seeding (incidents, postmortems, and runbooks).
     * Example: POST /api/knowledge/seed
     */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedAllKnowledge() {
        int seeded = knowledgeService.seedAllKnowledge();
        List<KnowledgeDocument> docs = knowledgeService.getAllDocuments();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "seededCount", seeded,
                "totalKnowledgeDocuments", docs.size(),
                "message", String.format("Seeded operational knowledge. Total indexed documents: %d", docs.size())
        ));
    }
}
