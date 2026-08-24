package com.aiincident.logprocessor.historical;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing an incident post-mortem record documenting root causes, impact, and action items.
 */
@Entity
@Table(
        name = "postmortems",
        indexes = {
                @Index(name = "idx_postmortem_id", columnList = "postmortem_id"),
                @Index(name = "idx_postmortem_incident_id", columnList = "incident_id"),
                @Index(name = "idx_postmortem_category", columnList = "category"),
                @Index(name = "idx_postmortem_severity", columnList = "severity")
        }
)
public class Postmortem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "postmortem_id", nullable = false, unique = true)
    private String postmortemId;

    @Column(name = "incident_id", nullable = false)
    private String incidentId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private HistoricalIncidentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AnomalySeverity severity;

    @Column(name = "lead_investigator")
    private String leadInvestigator;

    @Column(name = "executive_summary", nullable = false, columnDefinition = "TEXT")
    private String executiveSummary;

    @Column(name = "impact_summary", nullable = false, columnDefinition = "TEXT")
    private String impactSummary;

    @Column(name = "root_cause_analysis", nullable = false, columnDefinition = "TEXT")
    private String rootCauseAnalysis;

    @Column(name = "detection_and_response", nullable = false, columnDefinition = "TEXT")
    private String detectionAndResponse;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "postmortem_action_items", joinColumns = @JoinColumn(name = "postmortem_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "action_item", length = 2000)
    private List<String> actionItems = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "postmortem_lessons_learned", joinColumns = @JoinColumn(name = "postmortem_id"))
    @OrderColumn(name = "lesson_order")
    @Column(name = "lesson", length = 2000)
    private List<String> lessonsLearned = new ArrayList<>();

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Postmortem() {
    }

    public Postmortem(
            String postmortemId,
            String incidentId,
            String title,
            HistoricalIncidentCategory category,
            AnomalySeverity severity,
            String leadInvestigator,
            String executiveSummary,
            String impactSummary,
            String rootCauseAnalysis,
            String detectionAndResponse,
            List<String> actionItems,
            List<String> lessonsLearned,
            String content,
            Instant createdAt) {
        this.postmortemId = postmortemId;
        this.incidentId = incidentId;
        this.title = title;
        this.category = category;
        this.severity = severity != null ? severity : AnomalySeverity.HIGH;
        this.leadInvestigator = leadInvestigator != null ? leadInvestigator : "On-Call SRE";
        this.executiveSummary = executiveSummary;
        this.impactSummary = impactSummary;
        this.rootCauseAnalysis = rootCauseAnalysis;
        this.detectionAndResponse = detectionAndResponse;
        this.actionItems = actionItems != null ? new ArrayList<>(actionItems) : new ArrayList<>();
        this.lessonsLearned = lessonsLearned != null ? new ArrayList<>(lessonsLearned) : new ArrayList<>();
        this.content = content != null ? content : generateMarkdownContent();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public String generateMarkdownContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Postmortem: ").append(title).append("\n\n");
        sb.append("**Postmortem ID**: ").append(postmortemId).append("\n");
        sb.append("**Incident ID**: ").append(incidentId).append("\n");
        sb.append("**Category**: ").append(category).append("\n");
        sb.append("**Severity**: ").append(severity).append("\n");
        sb.append("**Lead Investigator**: ").append(leadInvestigator).append("\n\n");

        sb.append("## Executive Summary\n").append(executiveSummary).append("\n\n");
        sb.append("## Impact Summary\n").append(impactSummary).append("\n\n");
        sb.append("## Root Cause Analysis\n").append(rootCauseAnalysis).append("\n\n");
        sb.append("## Detection & Response\n").append(detectionAndResponse).append("\n\n");

        sb.append("## Action Items\n");
        for (int i = 0; i < actionItems.size(); i++) {
            sb.append(i + 1).append(". ").append(actionItems.get(i)).append("\n");
        }
        sb.append("\n");

        sb.append("## Lessons Learned\n");
        for (String lesson : lessonsLearned) {
            sb.append("- ").append(lesson).append("\n");
        }
        sb.append("\n");

        return sb.toString();
    }

    public Long getId() {
        return id;
    }

    public String getPostmortemId() {
        return postmortemId;
    }

    public void setPostmortemId(String postmortemId) {
        this.postmortemId = postmortemId;
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

    public String getLeadInvestigator() {
        return leadInvestigator;
    }

    public void setLeadInvestigator(String leadInvestigator) {
        this.leadInvestigator = leadInvestigator;
    }

    public String getExecutiveSummary() {
        return executiveSummary;
    }

    public void setExecutiveSummary(String executiveSummary) {
        this.executiveSummary = executiveSummary;
    }

    public String getImpactSummary() {
        return impactSummary;
    }

    public void setImpactSummary(String impactSummary) {
        this.impactSummary = impactSummary;
    }

    public String getRootCauseAnalysis() {
        return rootCauseAnalysis;
    }

    public void setRootCauseAnalysis(String rootCauseAnalysis) {
        this.rootCauseAnalysis = rootCauseAnalysis;
    }

    public String getDetectionAndResponse() {
        return detectionAndResponse;
    }

    public void setDetectionAndResponse(String detectionAndResponse) {
        this.detectionAndResponse = detectionAndResponse;
    }

    public List<String> getActionItems() {
        return actionItems;
    }

    public void setActionItems(List<String> actionItems) {
        this.actionItems = actionItems != null ? new ArrayList<>(actionItems) : new ArrayList<>();
    }

    public void addActionItem(String actionItem) {
        if (actionItem != null && !actionItem.isBlank()) {
            this.actionItems.add(actionItem.trim());
        }
    }

    public List<String> getLessonsLearned() {
        return lessonsLearned;
    }

    public void setLessonsLearned(List<String> lessonsLearned) {
        this.lessonsLearned = lessonsLearned != null ? new ArrayList<>(lessonsLearned) : new ArrayList<>();
    }

    public void addLessonLearned(String lesson) {
        if (lesson != null && !lesson.isBlank()) {
            this.lessonsLearned.add(lesson.trim());
        }
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
