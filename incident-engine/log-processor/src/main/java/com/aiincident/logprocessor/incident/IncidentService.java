package com.aiincident.logprocessor.incident;

import com.aiincident.logprocessor.anomaly.AnomalyEvent;
import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private static final Set<IncidentStatus> ACTIVE_STATUSES = Set.of(
            IncidentStatus.OPEN,
            IncidentStatus.INVESTIGATING
    );

    private final IncidentRepository incidentRepository;
    private final IncidentEvidenceRepository evidenceRepository;
    private final IncidentProperties properties;

    public IncidentService(IncidentRepository incidentRepository, IncidentProperties properties) {
        this(incidentRepository, null, properties);
    }

    @Autowired
    public IncidentService(
            IncidentRepository incidentRepository,
            @Autowired(required = false) IncidentEvidenceRepository evidenceRepository,
            IncidentProperties properties) {
        this.incidentRepository = incidentRepository;
        this.evidenceRepository = evidenceRepository;
        this.properties = properties;
    }

    /**
     * Process detected anomalies and convert them into incidents, preventing duplicates
     * during active failure windows.
     */
    @Transactional
    public List<Incident> processAnomalies(List<AnomalyEvent> anomalies) {
        if (anomalies == null || anomalies.isEmpty()) {
            return List.of();
        }

        Set<Incident> affectedIncidents = new LinkedHashSet<>();
        for (AnomalyEvent anomaly : anomalies) {
            createOrCorrelateIncident(anomaly).ifPresent(affectedIncidents::add);
        }

        return new ArrayList<>(affectedIncidents);
    }

    /**
     * Create a new incident or correlate to an existing active incident for the same service.
     */
    @Transactional
    public Optional<Incident> createOrCorrelateIncident(AnomalyEvent anomaly) {
        if (anomaly == null) {
            return Optional.empty();
        }

        // Check if anomaly severity crosses minimum incident threshold
        if (!isSeverityThresholdCrossed(anomaly.getSeverity(), properties.getMinIncidentSeverity())) {
            log.info("Anomaly for service '{}' [severity={}] below incident threshold [min={}]",
                    anomaly.getService(), anomaly.getSeverity(), properties.getMinIncidentSeverity());
            return Optional.empty();
        }

        String service = anomaly.getService() != null ? anomaly.getService().trim() : "unknown-service";
        Instant anomalyTime = anomaly.getWindowStart() != null ? anomaly.getWindowStart() : anomaly.getDetectedAt();

        // 1. Check for an existing active incident for this primary service (Duplicate Prevention)
        Optional<Incident> activeIncidentOpt = incidentRepository
                .findFirstByPrimaryServiceAndStatusInOrderByStartedAtDesc(service, ACTIVE_STATUSES);

        if (activeIncidentOpt.isPresent()) {
            Incident activeIncident = activeIncidentOpt.get();
            // Check if active incident is within active failure window
            Instant windowThreshold = activeIncident.getStartedAt()
                    .minus(Duration.ofMinutes(properties.getActiveWindowMinutes()));

            if (anomalyTime.isAfter(windowThreshold) || activeIncident.getStatus() == IncidentStatus.OPEN || activeIncident.getStatus() == IncidentStatus.INVESTIGATING) {
                // Correlate to existing active incident and upgrade severity if anomaly is higher
                if (isHigherSeverity(anomaly.getSeverity(), activeIncident.getSeverity())) {
                    log.info("Upgrading active incident #{} severity from {} to {} for service '{}'",
                            activeIncident.getId(), activeIncident.getSeverity(), anomaly.getSeverity(), service);
                    activeIncident.setSeverity(anomaly.getSeverity());
                } else {
                    log.info("Correlated anomaly to existing active incident #{} for service '{}'",
                            activeIncident.getId(), service);
                }

                if (anomaly.getDetectedAt() != null && (activeIncident.getLastEventAt() == null || anomaly.getDetectedAt().isAfter(activeIncident.getLastEventAt()))) {
                    activeIncident.setLastEventAt(anomaly.getDetectedAt());
                }

                activeIncident.addAffectedService(service);
                Incident saved = incidentRepository.save(activeIncident);
                attachEvidence(saved.getId(), anomaly);
                loadEvidence(saved);
                return Optional.of(saved);
            }
        }

        // 2. Create new Incident
        String title = buildIncidentTitle(anomaly);
        Incident newIncident = new Incident(
                title,
                anomaly.getSeverity(),
                IncidentStatus.OPEN,
                service,
                anomaly.getWindowStart() != null ? anomaly.getWindowStart() : anomaly.getDetectedAt(),
                anomaly.getDetectedAt() != null ? anomaly.getDetectedAt() : Instant.now(),
                anomaly.getMessage(),
                anomaly.getMetric()
        );
        newIncident.setRootService(service);
        newIncident.setLastEventAt(anomaly.getDetectedAt() != null ? anomaly.getDetectedAt() : Instant.now());
        newIncident.addAffectedService(service);

        Incident saved = incidentRepository.save(newIncident);
        log.warn("Created new incident #{} [title='{}', severity={}, service='{}', status={}]",
                saved.getId(), saved.getTitle(), saved.getSeverity(), saved.getPrimaryService(), saved.getStatus());

        attachEvidence(saved.getId(), anomaly);
        loadEvidence(saved);

        return Optional.of(saved);
    }

    private void attachEvidence(Long incidentId, AnomalyEvent anomaly) {
        if (evidenceRepository != null && incidentId != null && anomaly != null) {
            IncidentEvidence ev = new IncidentEvidence(
                    incidentId,
                    anomaly.getAnomalyId(),
                    anomaly.getDetectedAt() != null ? anomaly.getDetectedAt() : Instant.now(),
                    anomaly.getService(),
                    anomaly.getMetric() != null ? anomaly.getMetric() : "ANOMALY",
                    anomaly.getSeverity(),
                    anomaly.getMessage(),
                    null,
                    null
            );
            evidenceRepository.save(ev);
        }
    }

    /**
     * Transition the lifecycle status of an incident.
     */
    @Transactional
    public Incident updateStatus(Long id, IncidentStatus newStatus) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found with id: " + id));

        if (newStatus == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }

        IncidentStatus previousStatus = incident.getStatus();
        incident.setStatus(newStatus);

        if (newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.CLOSED) {
            if (incident.getResolvedAt() == null) {
                incident.setResolvedAt(Instant.now());
            }
        } else if (newStatus == IncidentStatus.OPEN || newStatus == IncidentStatus.INVESTIGATING) {
            // If reopened
            if (previousStatus == IncidentStatus.RESOLVED || previousStatus == IncidentStatus.CLOSED) {
                incident.setResolvedAt(null);
            }
        }

        Incident saved = incidentRepository.save(incident);
        loadEvidence(saved);
        log.info("Incident #{} status transitioned from {} to {}", id, previousStatus, newStatus);
        return saved;
    }

    /**
     * Acknowledge an incident (transitions from OPEN to INVESTIGATING).
     */
    @Transactional
    public Incident acknowledgeIncident(Long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found with id: " + id));

        if (incident.getStatus() != IncidentStatus.OPEN) {
            throw new IllegalStateException("Cannot acknowledge incident in status: " + incident.getStatus());
        }

        incident.setStatus(IncidentStatus.INVESTIGATING);
        Incident saved = incidentRepository.save(incident);
        loadEvidence(saved);
        log.info("Incident #{} acknowledged (status set to INVESTIGATING)", id);
        return saved;
    }

    /**
     * Resolve an incident (transitions to RESOLVED).
     */
    @Transactional
    public Incident resolveIncident(Long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found with id: " + id));

        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new IllegalStateException("Cannot resolve an already closed incident");
        }

        incident.setStatus(IncidentStatus.RESOLVED);
        if (incident.getResolvedAt() == null) {
            incident.setResolvedAt(Instant.now());
        }

        Incident saved = incidentRepository.save(incident);
        loadEvidence(saved);
        log.info("Incident #{} resolved (status set to RESOLVED, resolvedAt={})", id, saved.getResolvedAt());
        return saved;
    }

    /**
     * Close an incident (transitions to CLOSED).
     */
    @Transactional
    public Incident closeIncident(Long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found with id: " + id));

        incident.setStatus(IncidentStatus.CLOSED);
        if (incident.getResolvedAt() == null) {
            incident.setResolvedAt(Instant.now());
        }

        Incident saved = incidentRepository.save(incident);
        loadEvidence(saved);
        log.info("Incident #{} closed (status set to CLOSED)", id);
        return saved;
    }

    /**
     * Query incidents with multi-criteria filtering.
     */
    @Transactional(readOnly = true)
    public List<Incident> findIncidents(
            IncidentStatus status,
            AnomalySeverity severity,
            String service,
            Instant from,
            Instant to) {

        List<Incident> incidents = incidentRepository.findAll();

        return incidents.stream()
                .filter(i -> status == null || i.getStatus() == status)
                .filter(i -> severity == null || i.getSeverity() == severity)
                .filter(i -> service == null || service.isBlank() ||
                        (i.getPrimaryService() != null && i.getPrimaryService().equalsIgnoreCase(service.trim())) ||
                        (i.getAffectedServices() != null && i.getAffectedServices().stream().anyMatch(s -> s.equalsIgnoreCase(service.trim()))))
                .filter(i -> {
                    if (from == null && to == null) return true;
                    Instant time = i.getDetectedAt() != null ? i.getDetectedAt() : i.getStartedAt();
                    if (time == null) return false;
                    boolean afterFrom = (from == null || !time.isBefore(from));
                    boolean beforeTo = (to == null || !time.isAfter(to));
                    return afterFrom && beforeTo;
                })
                .peek(this::loadEvidence)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Incident> findAll() {
        List<Incident> list = incidentRepository.findAll();
        list.forEach(this::loadEvidence);
        return list;
    }

    @Transactional(readOnly = true)
    public Optional<Incident> findById(Long id) {
        Optional<Incident> opt = incidentRepository.findById(id);
        opt.ifPresent(this::loadEvidence);
        return opt;
    }

    @Transactional(readOnly = true)
    public List<Incident> findByStatus(IncidentStatus status) {
        List<Incident> list = incidentRepository.findByStatus(status);
        list.forEach(this::loadEvidence);
        return list;
    }

    @Transactional(readOnly = true)
    public List<Incident> findByService(String service) {
        List<Incident> list = incidentRepository.findByPrimaryService(service);
        list.forEach(this::loadEvidence);
        return list;
    }

    @Transactional(readOnly = true)
    public List<IncidentEvidence> getEvidenceByIncidentId(Long incidentId) {
        if (evidenceRepository == null || incidentId == null) {
            return List.of();
        }
        return evidenceRepository.findByIncidentIdOrderByTimestampAsc(incidentId);
    }

    private void loadEvidence(Incident incident) {
        if (incident != null && evidenceRepository != null && incident.getId() != null) {
            List<IncidentEvidence> evList = evidenceRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
            incident.setEvidence(evList);
        }
    }

    private String buildIncidentTitle(AnomalyEvent anomaly) {
        String service = anomaly.getService();
        String metric = anomaly.getMetric();
        if ("errorRate".equalsIgnoreCase(metric)) {
            return String.format("High Error Rate on %s", service);
        } else if ("latencyAvg".equalsIgnoreCase(metric)) {
            return String.format("Latency Spike on %s", service);
        } else if (metric != null && !metric.isBlank()) {
            return String.format("Operational Anomaly (%s) on %s", metric, service);
        }
        return String.format("Service Outage on %s", service);
    }

    private boolean isSeverityThresholdCrossed(AnomalySeverity actual, AnomalySeverity threshold) {
        if (actual == null) return false;
        if (threshold == null) return true;
        return actual.ordinal() >= threshold.ordinal();
    }

    private boolean isHigherSeverity(AnomalySeverity candidate, AnomalySeverity current) {
        if (candidate == null) return false;
        if (current == null) return true;
        return candidate.ordinal() > current.ordinal();
    }
}
