package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.historical.HistoricalIncidentCategory;
import com.aiincident.logprocessor.historical.KnowledgeDocument;
import com.aiincident.logprocessor.historical.KnowledgeDocumentService;
import com.aiincident.logprocessor.historical.KnowledgeDocumentType;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/knowledge", "/api/knowledge"})
public class KnowledgeController {

    private final KnowledgeDocumentService knowledgeService;

    public KnowledgeController(KnowledgeDocumentService knowledgeService) {
        this.knowledgeService = knowledgeService;
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
