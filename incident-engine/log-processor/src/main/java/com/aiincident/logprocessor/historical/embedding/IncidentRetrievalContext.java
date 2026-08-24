package com.aiincident.logprocessor.historical.embedding;

import java.util.List;

/**
 * Aggregated operational knowledge retrieval context for an active or historical incident.
 */
public class IncidentRetrievalContext {

    private String incidentId;
    private String title;
    private String primaryService;
    private String rootService;
    private String synthesizedSummary;
    private List<SemanticSearchResult> similarIncidents;
    private List<SemanticSearchResult> relevantRunbooks;
    private List<SemanticSearchResult> relevantPostmortems;

    public IncidentRetrievalContext() {
    }

    public IncidentRetrievalContext(
            String incidentId,
            String title,
            String primaryService,
            String rootService,
            String synthesizedSummary,
            List<SemanticSearchResult> similarIncidents,
            List<SemanticSearchResult> relevantRunbooks,
            List<SemanticSearchResult> relevantPostmortems) {
        this.incidentId = incidentId;
        this.title = title;
        this.primaryService = primaryService;
        this.rootService = rootService;
        this.synthesizedSummary = synthesizedSummary;
        this.similarIncidents = similarIncidents;
        this.relevantRunbooks = relevantRunbooks;
        this.relevantPostmortems = relevantPostmortems;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPrimaryService() {
        return primaryService;
    }

    public void setPrimaryService(String primaryService) {
        this.primaryService = primaryService;
    }

    public String getRootService() {
        return rootService;
    }

    public void setRootService(String rootService) {
        this.rootService = rootService;
    }

    public String getSynthesizedSummary() {
        return synthesizedSummary;
    }

    public void setSynthesizedSummary(String synthesizedSummary) {
        this.synthesizedSummary = synthesizedSummary;
    }

    public List<SemanticSearchResult> getSimilarIncidents() {
        return similarIncidents;
    }

    public void setSimilarIncidents(List<SemanticSearchResult> similarIncidents) {
        this.similarIncidents = similarIncidents;
    }

    public List<SemanticSearchResult> getRelevantRunbooks() {
        return relevantRunbooks;
    }

    public void setRelevantRunbooks(List<SemanticSearchResult> relevantRunbooks) {
        this.relevantRunbooks = relevantRunbooks;
    }

    public List<SemanticSearchResult> getRelevantPostmortems() {
        return relevantPostmortems;
    }

    public void setRelevantPostmortems(List<SemanticSearchResult> relevantPostmortems) {
        this.relevantPostmortems = relevantPostmortems;
    }
}
