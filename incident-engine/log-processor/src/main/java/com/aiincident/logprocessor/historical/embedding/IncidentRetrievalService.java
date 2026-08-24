package com.aiincident.logprocessor.historical.embedding;

import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentEvidence;
import com.aiincident.logprocessor.incident.IncidentEvidenceRepository;
import com.aiincident.logprocessor.incident.IncidentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service that connects active/current incidents to the operational knowledge retrieval pipeline.
 * Formulates incident summaries, executes semantic similarity search, and retrieves matching historical incidents and runbooks.
 */
@Service
public class IncidentRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(IncidentRetrievalService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentEvidenceRepository evidenceRepository;
    private final SemanticRetrievalService semanticRetrievalService;

    public IncidentRetrievalService(
            IncidentRepository incidentRepository,
            IncidentEvidenceRepository evidenceRepository,
            SemanticRetrievalService semanticRetrievalService) {
        this.incidentRepository = incidentRepository;
        this.evidenceRepository = evidenceRepository;
        this.semanticRetrievalService = semanticRetrievalService;
    }

    /**
     * Retrieve historically similar incidents for a given incident by ID.
     */
    public List<SemanticSearchResult> findSimilarIncidents(String incidentIdentifier, int topK) {
        Optional<Incident> incidentOpt = resolveIncident(incidentIdentifier);
        if (incidentOpt.isEmpty()) {
            return List.of();
        }

        Incident incident = incidentOpt.get();
        List<IncidentEvidence> evidence = getIncidentEvidence(incident);
        String summary = synthesizeIncidentSummary(incident, evidence);

        log.debug("Retrieving similar incidents for incident {} (topK={}) with query: {}", incident.getIncidentId(), topK, summary);
        return semanticRetrievalService.findSimilarIncidents(summary, topK);
    }

    /**
     * Retrieve relevant operational runbooks for mitigating a given incident.
     */
    public List<SemanticSearchResult> findRelevantRunbooks(String incidentIdentifier, int topK) {
        Optional<Incident> incidentOpt = resolveIncident(incidentIdentifier);
        if (incidentOpt.isEmpty()) {
            return List.of();
        }

        Incident incident = incidentOpt.get();
        List<IncidentEvidence> evidence = getIncidentEvidence(incident);
        String summary = synthesizeIncidentSummary(incident, evidence);

        log.debug("Retrieving relevant runbooks for incident {} (topK={}) with query: {}", incident.getIncidentId(), topK, summary);
        return semanticRetrievalService.findRelevantRunbooks(summary, topK);
    }

    /**
     * Retrieve relevant post-mortems for a given incident.
     */
    public List<SemanticSearchResult> findRelevantPostmortems(String incidentIdentifier, int topK) {
        Optional<Incident> incidentOpt = resolveIncident(incidentIdentifier);
        if (incidentOpt.isEmpty()) {
            return List.of();
        }

        Incident incident = incidentOpt.get();
        List<IncidentEvidence> evidence = getIncidentEvidence(incident);
        String summary = synthesizeIncidentSummary(incident, evidence);

        log.debug("Retrieving relevant postmortems for incident {} (topK={}) with query: {}", incident.getIncidentId(), topK, summary);
        return semanticRetrievalService.findRelevantPostmortems(summary, topK);
    }

    /**
     * Build full incident retrieval context containing synthesized summary, similar incidents, and relevant runbooks.
     */
    public Optional<IncidentRetrievalContext> getIncidentRetrievalContext(String incidentIdentifier, int topK) {
        Optional<Incident> incidentOpt = resolveIncident(incidentIdentifier);
        if (incidentOpt.isEmpty()) {
            return Optional.empty();
        }

        Incident incident = incidentOpt.get();
        List<IncidentEvidence> evidence = getIncidentEvidence(incident);
        String summary = synthesizeIncidentSummary(incident, evidence);

        List<SemanticSearchResult> similarIncidents = semanticRetrievalService.findSimilarIncidents(summary, topK);
        List<SemanticSearchResult> relevantRunbooks = semanticRetrievalService.findRelevantRunbooks(summary, topK);
        List<SemanticSearchResult> relevantPostmortems = semanticRetrievalService.findRelevantPostmortems(summary, topK);

        return Optional.of(new IncidentRetrievalContext(
                incident.getIncidentId() != null ? incident.getIncidentId() : String.valueOf(incident.getId()),
                incident.getTitle(),
                incident.getPrimaryService(),
                incident.getRootService(),
                summary,
                similarIncidents,
                relevantRunbooks,
                relevantPostmortems
        ));
    }

    /**
     * Synthesizes a search query representation from an incident entity and its correlated event evidence.
     */
    public String synthesizeIncidentSummary(Incident incident, List<IncidentEvidence> evidence) {
        StringBuilder sb = new StringBuilder();

        if (incident.getTitle() != null && !incident.getTitle().isBlank()) {
            sb.append(incident.getTitle()).append(". ");
        }

        if (incident.getPrimaryService() != null) {
            sb.append("Primary service: ").append(incident.getPrimaryService()).append(". ");
        }

        if (incident.getRootService() != null && !incident.getRootService().equals(incident.getPrimaryService())) {
            sb.append("Root cause service: ").append(incident.getRootService()).append(". ");
        }

        if (incident.getDescription() != null && !incident.getDescription().isBlank()) {
            sb.append(incident.getDescription()).append(". ");
        }

        if (incident.getAffectedServices() != null && !incident.getAffectedServices().isEmpty()) {
            sb.append("Affected services: ").append(String.join(", ", incident.getAffectedServices())).append(". ");
        }

        if (evidence != null && !evidence.isEmpty()) {
            String eventTypes = evidence.stream()
                    .map(IncidentEvidence::getEventType)
                    .filter(et -> et != null && !et.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));
            if (!eventTypes.isBlank()) {
                sb.append("Observed error events: ").append(eventTypes).append(". ");
            }

            // Extract sample error messages
            List<String> messages = evidence.stream()
                    .map(IncidentEvidence::getMessage)
                    .filter(m -> m != null && !m.isBlank())
                    .distinct()
                    .limit(3)
                    .toList();
            if (!messages.isEmpty()) {
                sb.append("Error details: ").append(String.join("; ", messages)).append(".");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Resolve incident by either numeric database ID or string incidentId (UUID/slug).
     */
    public Optional<Incident> resolveIncident(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        // Try numeric ID
        try {
            Long numericId = Long.parseLong(identifier.trim());
            Optional<Incident> byId = incidentRepository.findById(numericId);
            if (byId.isPresent()) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
        }

        // Try string incidentId (e.g. UUID)
        return incidentRepository.findByIncidentId(identifier.trim());
    }

    private List<IncidentEvidence> getIncidentEvidence(Incident incident) {
        if (incident.getId() != null) {
            return evidenceRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
        }
        return List.of();
    }
}
