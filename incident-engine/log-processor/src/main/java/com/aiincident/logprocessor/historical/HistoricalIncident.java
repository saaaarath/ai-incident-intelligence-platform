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
 * Entity representing a historical operational incident and post-mortem knowledge record.
 */
@Entity
@Table(
        name = "historical_incidents",
        indexes = {
                @Index(name = "idx_hist_inc_category", columnList = "category"),
                @Index(name = "idx_hist_inc_incident_id", columnList = "incident_id"),
                @Index(name = "idx_hist_inc_severity", columnList = "severity"),
                @Index(name = "idx_hist_inc_occurred_at", columnList = "occurred_at")
        }
)
public class HistoricalIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false, unique = true)
    private String incidentId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private HistoricalIncidentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AnomalySeverity severity;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "historical_incident_symptoms", joinColumns = @JoinColumn(name = "incident_id"))
    @OrderColumn(name = "symptom_order")
    @Column(name = "symptom", length = 1000)
    private List<String> symptoms = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "historical_incident_timeline", joinColumns = @JoinColumn(name = "incident_id"))
    @OrderColumn(name = "timeline_order")
    @Column(name = "timeline_entry", length = 1000)
    private List<String> timeline = new ArrayList<>();

    @Column(name = "root_cause", nullable = false, columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "resolution", nullable = false, columnDefinition = "TEXT")
    private String resolution;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "historical_incident_affected_services", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "service_name")
    private Set<String> affectedServices = new HashSet<>();

    @Column(name = "prevention", nullable = false, columnDefinition = "TEXT")
    private String prevention;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    public HistoricalIncident() {
    }

    public HistoricalIncident(
            String incidentId,
            String title,
            HistoricalIncidentCategory category,
            AnomalySeverity severity,
            List<String> symptoms,
            List<String> timeline,
            String rootCause,
            String resolution,
            Set<String> affectedServices,
            String prevention,
            Instant occurredAt,
            Integer durationMinutes) {
        this.incidentId = incidentId;
        this.title = title;
        this.category = category;
        this.severity = severity != null ? severity : AnomalySeverity.HIGH;
        this.symptoms = symptoms != null ? new ArrayList<>(symptoms) : new ArrayList<>();
        this.timeline = timeline != null ? new ArrayList<>(timeline) : new ArrayList<>();
        this.rootCause = rootCause;
        this.resolution = resolution;
        this.affectedServices = affectedServices != null ? new HashSet<>(affectedServices) : new HashSet<>();
        this.prevention = prevention;
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
        this.durationMinutes = durationMinutes != null ? durationMinutes : 15;
    }

    public Long getId() {
        return id;
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

    public List<String> getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(List<String> symptoms) {
        this.symptoms = symptoms != null ? new ArrayList<>(symptoms) : new ArrayList<>();
    }

    public void addSymptom(String symptom) {
        if (symptom != null && !symptom.isBlank()) {
            this.symptoms.add(symptom.trim());
        }
    }

    public List<String> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<String> timeline) {
        this.timeline = timeline != null ? new ArrayList<>(timeline) : new ArrayList<>();
    }

    public void addTimelineEntry(String entry) {
        if (entry != null && !entry.isBlank()) {
            this.timeline.add(entry.trim());
        }
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public Set<String> getAffectedServices() {
        return affectedServices;
    }

    public void setAffectedServices(Set<String> affectedServices) {
        this.affectedServices = affectedServices != null ? new HashSet<>(affectedServices) : new HashSet<>();
    }

    public void addAffectedService(String service) {
        if (service != null && !service.isBlank()) {
            this.affectedServices.add(service.trim());
        }
    }

    public String getPrevention() {
        return prevention;
    }

    public void setPrevention(String prevention) {
        this.prevention = prevention;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
}
