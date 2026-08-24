package com.aiincident.logprocessor.rca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA Entity persisting AI Root Cause Analysis (RCA) reports associated with an Incident.
 */
@Entity
@Table(
        name = "rca_reports",
        indexes = {
                @Index(name = "idx_rca_incident_id", columnList = "incident_id"),
                @Index(name = "idx_rca_num_incident_id", columnList = "numeric_incident_id"),
                @Index(name = "idx_rca_created_at", columnList = "created_at")
        }
)
public class RcaReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private String incidentId;

    @Column(name = "numeric_incident_id")
    private Long numericIncidentId;

    @Column(name = "root_cause_statement", columnDefinition = "TEXT")
    private String rootCauseStatement;

    @Column(name = "root_cause_category")
    private String rootCauseCategory;

    @Column(name = "root_service")
    private String rootService;

    @Column(name = "root_cause_inference_details", columnDefinition = "TEXT")
    private String rootCauseInferenceDetails;

    @Column(name = "is_directly_observed")
    private boolean isDirectlyObserved;

    @Column(name = "confidence_level")
    private String confidenceLevel;

    @Column(name = "confidence_score")
    private double confidenceScore;

    @Column(name = "confidence_rationale", columnDefinition = "TEXT")
    private String confidenceRationale;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "alternative_hypotheses_json", columnDefinition = "TEXT")
    private String alternativeHypothesesJson;

    @Column(name = "affected_services_json", columnDefinition = "TEXT")
    private String affectedServicesJson;

    @Column(name = "recommendations_json", columnDefinition = "TEXT")
    private String recommendationsJson;

    @Column(name = "historical_references_json", columnDefinition = "TEXT")
    private String historicalReferencesJson;

    @Column(name = "uncertainty_notes_json", columnDefinition = "TEXT")
    private String uncertaintyNotesJson;

    @Column(name = "validation_status")
    private String validationStatus;

    @Column(name = "validation_json", columnDefinition = "TEXT")
    private String validationJson;

    @Column(name = "full_report_json", columnDefinition = "TEXT")
    private String fullReportJson;

    @Column(name = "provider")
    private String provider;

    @Column(name = "model")
    private String model;

    @Column(name = "execution_latency_ms")
    private long executionLatencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RcaReportEntity() {
    }

    public RcaReportEntity(
            String incidentId,
            Long numericIncidentId,
            String rootCauseStatement,
            String rootCauseCategory,
            String rootService,
            String rootCauseInferenceDetails,
            boolean isDirectlyObserved,
            String confidenceLevel,
            double confidenceScore,
            String confidenceRationale,
            String evidenceJson,
            String alternativeHypothesesJson,
            String affectedServicesJson,
            String recommendationsJson,
            String historicalReferencesJson,
            String uncertaintyNotesJson,
            String validationStatus,
            String validationJson,
            String fullReportJson,
            String provider,
            String model,
            long executionLatencyMs,
            Instant createdAt) {
        this.incidentId = incidentId;
        this.numericIncidentId = numericIncidentId;
        this.rootCauseStatement = rootCauseStatement;
        this.rootCauseCategory = rootCauseCategory;
        this.rootService = rootService;
        this.rootCauseInferenceDetails = rootCauseInferenceDetails;
        this.isDirectlyObserved = isDirectlyObserved;
        this.confidenceLevel = confidenceLevel;
        this.confidenceScore = confidenceScore;
        this.confidenceRationale = confidenceRationale;
        this.evidenceJson = evidenceJson;
        this.alternativeHypothesesJson = alternativeHypothesesJson;
        this.affectedServicesJson = affectedServicesJson;
        this.recommendationsJson = recommendationsJson;
        this.historicalReferencesJson = historicalReferencesJson;
        this.uncertaintyNotesJson = uncertaintyNotesJson;
        this.validationStatus = validationStatus;
        this.validationJson = validationJson;
        this.fullReportJson = fullReportJson;
        this.provider = provider;
        this.model = model;
        this.executionLatencyMs = executionLatencyMs;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public Long getNumericIncidentId() {
        return numericIncidentId;
    }

    public void setNumericIncidentId(Long numericIncidentId) {
        this.numericIncidentId = numericIncidentId;
    }

    public String getRootCauseStatement() {
        return rootCauseStatement;
    }

    public void setRootCauseStatement(String rootCauseStatement) {
        this.rootCauseStatement = rootCauseStatement;
    }

    public String getRootCauseCategory() {
        return rootCauseCategory;
    }

    public void setRootCauseCategory(String rootCauseCategory) {
        this.rootCauseCategory = rootCauseCategory;
    }

    public String getRootService() {
        return rootService;
    }

    public void setRootService(String rootService) {
        this.rootService = rootService;
    }

    public String getRootCauseInferenceDetails() {
        return rootCauseInferenceDetails;
    }

    public void setRootCauseInferenceDetails(String rootCauseInferenceDetails) {
        this.rootCauseInferenceDetails = rootCauseInferenceDetails;
    }

    public boolean isDirectlyObserved() {
        return isDirectlyObserved;
    }

    public void setDirectlyObserved(boolean directlyObserved) {
        isDirectlyObserved = directlyObserved;
    }

    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(String confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getConfidenceRationale() {
        return confidenceRationale;
    }

    public void setConfidenceRationale(String confidenceRationale) {
        this.confidenceRationale = confidenceRationale;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(String evidenceJson) {
        this.evidenceJson = evidenceJson;
    }

    public String getAlternativeHypothesesJson() {
        return alternativeHypothesesJson;
    }

    public void setAlternativeHypothesesJson(String alternativeHypothesesJson) {
        this.alternativeHypothesesJson = alternativeHypothesesJson;
    }

    public String getAffectedServicesJson() {
        return affectedServicesJson;
    }

    public void setAffectedServicesJson(String affectedServicesJson) {
        this.affectedServicesJson = affectedServicesJson;
    }

    public String getRecommendationsJson() {
        return recommendationsJson;
    }

    public void setRecommendationsJson(String recommendationsJson) {
        this.recommendationsJson = recommendationsJson;
    }

    public String getHistoricalReferencesJson() {
        return historicalReferencesJson;
    }

    public void setHistoricalReferencesJson(String historicalReferencesJson) {
        this.historicalReferencesJson = historicalReferencesJson;
    }

    public String getUncertaintyNotesJson() {
        return uncertaintyNotesJson;
    }

    public void setUncertaintyNotesJson(String uncertaintyNotesJson) {
        this.uncertaintyNotesJson = uncertaintyNotesJson;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getValidationJson() {
        return validationJson;
    }

    public void setValidationJson(String validationJson) {
        this.validationJson = validationJson;
    }

    public String getFullReportJson() {
        return fullReportJson;
    }

    public void setFullReportJson(String fullReportJson) {
        this.fullReportJson = fullReportJson;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getExecutionLatencyMs() {
        return executionLatencyMs;
    }

    public void setExecutionLatencyMs(long executionLatencyMs) {
        this.executionLatencyMs = executionLatencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
