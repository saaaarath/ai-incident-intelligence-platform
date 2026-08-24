package com.aiincident.logprocessor.historical;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unified operational knowledge document representation structured for text search and future embedding/RAG retrieval.
 */
public class KnowledgeDocument {

    private String documentId;
    private KnowledgeDocumentType documentType;
    private String title;
    private HistoricalIncidentCategory category;
    private AnomalySeverity severity;
    private Set<String> relatedServices = new HashSet<>();
    private Set<String> tags = new HashSet<>();
    private String content;
    private Map<String, Object> metadata = new HashMap<>();

    public KnowledgeDocument() {
    }

    public KnowledgeDocument(
            String documentId,
            KnowledgeDocumentType documentType,
            String title,
            HistoricalIncidentCategory category,
            AnomalySeverity severity,
            Set<String> relatedServices,
            Set<String> tags,
            String content,
            Map<String, Object> metadata) {
        this.documentId = documentId;
        this.documentType = documentType;
        this.title = title;
        this.category = category;
        this.severity = severity;
        this.relatedServices = relatedServices != null ? new HashSet<>(relatedServices) : new HashSet<>();
        this.tags = tags != null ? new HashSet<>(tags) : new HashSet<>();
        this.content = content;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public static KnowledgeDocument fromIncident(HistoricalIncident incident) {
        if (incident == null) {
            return null;
        }

        StringBuilder content = new StringBuilder();
        content.append("# Incident: ").append(incident.getTitle()).append("\n\n");
        content.append("**Incident ID**: ").append(incident.getIncidentId()).append("\n");
        content.append("**Category**: ").append(incident.getCategory()).append("\n");
        content.append("**Severity**: ").append(incident.getSeverity()).append("\n");
        content.append("**Affected Services**: ").append(String.join(", ", incident.getAffectedServices())).append("\n");
        content.append("**Occurred At**: ").append(incident.getOccurredAt()).append("\n");
        content.append("**Duration**: ").append(incident.getDurationMinutes()).append(" minutes\n\n");

        content.append("## Symptoms\n");
        for (String sym : incident.getSymptoms()) {
            content.append("- ").append(sym).append("\n");
        }
        content.append("\n");

        content.append("## Timeline\n");
        for (String time : incident.getTimeline()) {
            content.append("- ").append(time).append("\n");
        }
        content.append("\n");

        content.append("## Root Cause\n").append(incident.getRootCause()).append("\n\n");
        content.append("## Resolution\n").append(incident.getResolution()).append("\n\n");
        content.append("## Prevention\n").append(incident.getPrevention()).append("\n");

        Map<String, Object> meta = new HashMap<>();
        meta.put("incidentId", incident.getIncidentId());
        meta.put("durationMinutes", incident.getDurationMinutes());
        meta.put("occurredAt", incident.getOccurredAt().toString());

        Set<String> tags = new HashSet<>();
        tags.add(incident.getCategory().name().toLowerCase());
        tags.addAll(incident.getAffectedServices());

        return new KnowledgeDocument(
                "INC:" + incident.getIncidentId(),
                KnowledgeDocumentType.HISTORICAL_INCIDENT,
                incident.getTitle(),
                incident.getCategory(),
                incident.getSeverity(),
                incident.getAffectedServices(),
                tags,
                content.toString(),
                meta
        );
    }

    public static KnowledgeDocument fromPostmortem(Postmortem postmortem, Set<String> affectedServices) {
        if (postmortem == null) {
            return null;
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("postmortemId", postmortem.getPostmortemId());
        meta.put("incidentId", postmortem.getIncidentId());
        meta.put("leadInvestigator", postmortem.getLeadInvestigator());
        meta.put("createdAt", postmortem.getCreatedAt().toString());

        Set<String> tags = new HashSet<>();
        tags.add(postmortem.getCategory().name().toLowerCase());
        tags.add("postmortem");
        if (affectedServices != null) {
            tags.addAll(affectedServices);
        }

        return new KnowledgeDocument(
                "PM:" + postmortem.getPostmortemId(),
                KnowledgeDocumentType.POSTMORTEM,
                postmortem.getTitle(),
                postmortem.getCategory(),
                postmortem.getSeverity(),
                affectedServices != null ? affectedServices : Set.of(),
                tags,
                postmortem.getContent(),
                meta
        );
    }

    public static KnowledgeDocument fromRunbook(Runbook runbook) {
        if (runbook == null) {
            return null;
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("runbookId", runbook.getRunbookId());
        meta.put("escalationPath", runbook.getEscalationPath());
        meta.put("updatedAt", runbook.getUpdatedAt().toString());

        Set<String> tags = new HashSet<>(runbook.getTags());
        tags.add(runbook.getCategory().name().toLowerCase());
        tags.add("runbook");

        return new KnowledgeDocument(
                "RB:" + runbook.getRunbookId(),
                KnowledgeDocumentType.RUNBOOK,
                runbook.getTitle(),
                runbook.getCategory(),
                runbook.getSeverity(),
                runbook.getApplicableServices(),
                tags,
                runbook.getContent(),
                meta
        );
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public KnowledgeDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(KnowledgeDocumentType documentType) {
        this.documentType = documentType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public HistoricalIncidentCategory getCategory() {
        return category;
    }

    public void setCategory(HistoricalIncidentCategory category) {
        this.category = category;
    }

    public AnomalySeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AnomalySeverity severity) {
        this.severity = severity;
    }

    public Set<String> getRelatedServices() {
        return relatedServices;
    }

    public void setRelatedServices(Set<String> relatedServices) {
        this.relatedServices = relatedServices != null ? new HashSet<>(relatedServices) : new HashSet<>();
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags != null ? new HashSet<>(tags) : new HashSet<>();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
}
