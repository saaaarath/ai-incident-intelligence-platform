package com.aiincident.logprocessor.historical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service providing unified document search and retrieval across historical incidents, postmortems, and runbooks.
 */
@Service
public class KnowledgeDocumentService {

    private final HistoricalIncidentRepository incidentRepository;
    private final RunbookRepository runbookRepository;
    private final PostmortemRepository postmortemRepository;
    private final HistoricalIncidentSeeder seeder;

    public KnowledgeDocumentService(
            HistoricalIncidentRepository incidentRepository,
            RunbookRepository runbookRepository,
            PostmortemRepository postmortemRepository,
            HistoricalIncidentSeeder seeder) {
        this.incidentRepository = incidentRepository;
        this.runbookRepository = runbookRepository;
        this.postmortemRepository = postmortemRepository;
        this.seeder = seeder;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> getAllDocuments() {
        List<KnowledgeDocument> documents = new ArrayList<>();

        Map<String, Set<String>> incidentServicesMap = new HashMap<>();
        List<HistoricalIncident> incidents = incidentRepository.findAll();
        for (HistoricalIncident inc : incidents) {
            incidentServicesMap.put(inc.getIncidentId(), inc.getAffectedServices());
            documents.add(KnowledgeDocument.fromIncident(inc));
        }

        List<Postmortem> postmortems = postmortemRepository.findAll();
        for (Postmortem pm : postmortems) {
            Set<String> affected = incidentServicesMap.getOrDefault(pm.getIncidentId(), Set.of());
            documents.add(KnowledgeDocument.fromPostmortem(pm, affected));
        }

        List<Runbook> runbooks = runbookRepository.findAll();
        for (Runbook rb : runbooks) {
            documents.add(KnowledgeDocument.fromRunbook(rb));
        }

        return documents;
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeDocument> getDocumentById(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return Optional.empty();
        }

        String rawId = documentId.trim();
        if (rawId.startsWith("INC:")) {
            String id = rawId.substring(4);
            return incidentRepository.findByIncidentId(id)
                    .map(KnowledgeDocument::fromIncident);
        } else if (rawId.startsWith("PM:")) {
            String id = rawId.substring(3);
            return postmortemRepository.findByPostmortemId(id)
                    .map(pm -> {
                        Set<String> affected = incidentRepository.findByIncidentId(pm.getIncidentId())
                                .map(HistoricalIncident::getAffectedServices)
                                .orElse(Set.of());
                        return KnowledgeDocument.fromPostmortem(pm, affected);
                    });
        } else if (rawId.startsWith("RB:")) {
            String id = rawId.substring(3);
            return runbookRepository.findByRunbookId(id)
                    .map(KnowledgeDocument::fromRunbook);
        }

        // Try direct lookup across repositories
        Optional<HistoricalIncident> inc = incidentRepository.findByIncidentId(rawId);
        if (inc.isPresent()) {
            return inc.map(KnowledgeDocument::fromIncident);
        }

        Optional<Postmortem> pm = postmortemRepository.findByPostmortemId(rawId);
        if (pm.isPresent()) {
            Set<String> affected = incidentRepository.findByIncidentId(pm.get().getIncidentId())
                    .map(HistoricalIncident::getAffectedServices)
                    .orElse(Set.of());
            return pm.map(p -> KnowledgeDocument.fromPostmortem(p, affected));
        }

        Optional<Runbook> rb = runbookRepository.findByRunbookId(rawId);
        if (rb.isPresent()) {
            return rb.map(KnowledgeDocument::fromRunbook);
        }

        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> search(
            String query,
            KnowledgeDocumentType type,
            HistoricalIncidentCategory category,
            String service) {

        List<KnowledgeDocument> allDocs = getAllDocuments();
        String normalizedQuery = query != null ? query.trim().toLowerCase() : null;
        String normalizedService = service != null ? service.trim().toLowerCase() : null;

        return allDocs.stream()
                .filter(doc -> type == null || doc.getDocumentType() == type)
                .filter(doc -> category == null || doc.getCategory() == category)
                .filter(doc -> normalizedService == null ||
                        doc.getRelatedServices().stream().anyMatch(s -> s.equalsIgnoreCase(normalizedService)) ||
                        doc.getTags().stream().anyMatch(t -> t.equalsIgnoreCase(normalizedService)))
                .filter(doc -> {
                    if (normalizedQuery == null || normalizedQuery.isBlank()) {
                        return true;
                    }
                    return (doc.getTitle() != null && doc.getTitle().toLowerCase().contains(normalizedQuery)) ||
                           (doc.getContent() != null && doc.getContent().toLowerCase().contains(normalizedQuery)) ||
                           (doc.getDocumentId() != null && doc.getDocumentId().toLowerCase().contains(normalizedQuery)) ||
                           doc.getTags().stream().anyMatch(t -> t.toLowerCase().contains(normalizedQuery));
                })
                .toList();
    }

    @Transactional
    public int seedAllKnowledge() {
        return seeder.seedAllOperationalKnowledge();
    }
}
