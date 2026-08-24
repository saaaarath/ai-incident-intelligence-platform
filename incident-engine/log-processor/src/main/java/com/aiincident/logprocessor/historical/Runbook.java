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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entity representing an operational runbook for diagnosing and mitigating production failures.
 */
@Entity
@Table(
        name = "runbooks",
        indexes = {
                @Index(name = "idx_runbook_id", columnList = "runbook_id"),
                @Index(name = "idx_runbook_category", columnList = "category"),
                @Index(name = "idx_runbook_severity", columnList = "severity")
        }
)
public class Runbook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "runbook_id", nullable = false, unique = true)
    private String runbookId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private HistoricalIncidentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AnomalySeverity severity;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "runbook_applicable_services", joinColumns = @JoinColumn(name = "runbook_id"))
    @Column(name = "service_name")
    private Set<String> applicableServices = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "runbook_trigger_symptoms", joinColumns = @JoinColumn(name = "runbook_id"))
    @OrderColumn(name = "symptom_order")
    @Column(name = "symptom", length = 1000)
    private List<String> triggerSymptoms = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "runbook_prerequisites", joinColumns = @JoinColumn(name = "runbook_id"))
    @OrderColumn(name = "prereq_order")
    @Column(name = "prerequisite", length = 1000)
    private List<String> prerequisites = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "runbook_mitigation_steps", joinColumns = @JoinColumn(name = "runbook_id"))
    @OrderColumn(name = "step_order")
    @Column(name = "step", length = 2000)
    private List<String> mitigationSteps = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "runbook_verification_steps", joinColumns = @JoinColumn(name = "runbook_id"))
    @OrderColumn(name = "step_order")
    @Column(name = "step", length = 2000)
    private List<String> verificationSteps = new ArrayList<>();

    @Column(name = "escalation_path", nullable = false, columnDefinition = "TEXT")
    private String escalationPath;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "runbook_tags", joinColumns = @JoinColumn(name = "runbook_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Runbook() {
    }

    public Runbook(
            String runbookId,
            String title,
            HistoricalIncidentCategory category,
            AnomalySeverity severity,
            Set<String> applicableServices,
            List<String> triggerSymptoms,
            List<String> prerequisites,
            List<String> mitigationSteps,
            List<String> verificationSteps,
            String escalationPath,
            String content,
            Set<String> tags,
            Instant updatedAt) {
        this.runbookId = runbookId;
        this.title = title;
        this.category = category;
        this.severity = severity != null ? severity : AnomalySeverity.HIGH;
        this.applicableServices = applicableServices != null ? new HashSet<>(applicableServices) : new HashSet<>();
        this.triggerSymptoms = triggerSymptoms != null ? new ArrayList<>(triggerSymptoms) : new ArrayList<>();
        this.prerequisites = prerequisites != null ? new ArrayList<>(prerequisites) : new ArrayList<>();
        this.mitigationSteps = mitigationSteps != null ? new ArrayList<>(mitigationSteps) : new ArrayList<>();
        this.verificationSteps = verificationSteps != null ? new ArrayList<>(verificationSteps) : new ArrayList<>();
        this.escalationPath = escalationPath;
        this.content = content != null ? content : generateMarkdownContent();
        this.tags = tags != null ? new HashSet<>(tags) : new HashSet<>();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public String generateMarkdownContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("**ID**: ").append(runbookId).append("\n");
        sb.append("**Category**: ").append(category).append("\n");
        sb.append("**Severity**: ").append(severity).append("\n");
        sb.append("**Applicable Services**: ").append(String.join(", ", applicableServices)).append("\n\n");

        sb.append("## Trigger Symptoms\n");
        for (String sym : triggerSymptoms) {
            sb.append("- ").append(sym).append("\n");
        }
        sb.append("\n");

        sb.append("## Prerequisites\n");
        for (String pre : prerequisites) {
            sb.append("- ").append(pre).append("\n");
        }
        sb.append("\n");

        sb.append("## Mitigation Steps\n");
        for (int i = 0; i < mitigationSteps.size(); i++) {
            sb.append(i + 1).append(". ").append(mitigationSteps.get(i)).append("\n");
        }
        sb.append("\n");

        sb.append("## Verification Steps\n");
        for (int i = 0; i < verificationSteps.size(); i++) {
            sb.append(i + 1).append(". ").append(verificationSteps.get(i)).append("\n");
        }
        sb.append("\n");

        sb.append("## Escalation Path\n").append(escalationPath).append("\n");
        return sb.toString();
    }

    public Long getId() {
        return id;
    }

    public String getRunbookId() {
        return runbookId;
    }

    public void setRunbookId(String runbookId) {
        this.runbookId = runbookId;
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

    public Set<String> getApplicableServices() {
        return applicableServices;
    }

    public void setApplicableServices(Set<String> applicableServices) {
        this.applicableServices = applicableServices != null ? new HashSet<>(applicableServices) : new HashSet<>();
    }

    public void addApplicableService(String service) {
        if (service != null && !service.isBlank()) {
            this.applicableServices.add(service.trim());
        }
    }

    public List<String> getTriggerSymptoms() {
        return triggerSymptoms;
    }

    public void setTriggerSymptoms(List<String> triggerSymptoms) {
        this.triggerSymptoms = triggerSymptoms != null ? new ArrayList<>(triggerSymptoms) : new ArrayList<>();
    }

    public void addTriggerSymptom(String symptom) {
        if (symptom != null && !symptom.isBlank()) {
            this.triggerSymptoms.add(symptom.trim());
        }
    }

    public List<String> getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(List<String> prerequisites) {
        this.prerequisites = prerequisites != null ? new ArrayList<>(prerequisites) : new ArrayList<>();
    }

    public void addPrerequisite(String prerequisite) {
        if (prerequisite != null && !prerequisite.isBlank()) {
            this.prerequisites.add(prerequisite.trim());
        }
    }

    public List<String> getMitigationSteps() {
        return mitigationSteps;
    }

    public void setMitigationSteps(List<String> mitigationSteps) {
        this.mitigationSteps = mitigationSteps != null ? new ArrayList<>(mitigationSteps) : new ArrayList<>();
    }

    public void addMitigationStep(String step) {
        if (step != null && !step.isBlank()) {
            this.mitigationSteps.add(step.trim());
        }
    }

    public List<String> getVerificationSteps() {
        return verificationSteps;
    }

    public void setVerificationSteps(List<String> verificationSteps) {
        this.verificationSteps = verificationSteps != null ? new ArrayList<>(verificationSteps) : new ArrayList<>();
    }

    public void addVerificationStep(String step) {
        if (step != null && !step.isBlank()) {
            this.verificationSteps.add(step.trim());
        }
    }

    public String getEscalationPath() {
        return escalationPath;
    }

    public void setEscalationPath(String escalationPath) {
        this.escalationPath = escalationPath;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags != null ? new HashSet<>(tags) : new HashSet<>();
    }

    public void addTag(String tag) {
        if (tag != null && !tag.isBlank()) {
            this.tags.add(tag.trim().toLowerCase());
        }
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
