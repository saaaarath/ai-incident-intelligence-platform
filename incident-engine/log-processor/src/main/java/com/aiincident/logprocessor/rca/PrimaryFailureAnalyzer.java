package com.aiincident.logprocessor.rca;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.dependency.ServiceDependency;
import com.aiincident.logprocessor.dependency.ServiceDependencyService;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentEvidence;
import com.aiincident.logprocessor.incident.IncidentEvidenceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Deterministic Primary Failure vs Downstream Symptoms Analyzer.
 * Identifies the likely root-cause primary failure across multi-service cascading incidents
 * by scoring temporal precedence, dependency topology position, error severity, and frequency without an LLM.
 */
@Component
public class PrimaryFailureAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(PrimaryFailureAnalyzer.class);

    private final ServiceDependencyService dependencyService;
    private final IncidentEvidenceRepository evidenceRepository;

    public PrimaryFailureAnalyzer(
            @Autowired(required = false) ServiceDependencyService dependencyService,
            @Autowired(required = false) IncidentEvidenceRepository evidenceRepository) {
        this.dependencyService = dependencyService;
        this.evidenceRepository = evidenceRepository;
    }

    /**
     * Analyze an incident and determine its primary failure vs downstream symptoms.
     */
    public PrimaryFailureAnalysis analyzeIncident(Incident incident) {
        if (incident == null) {
            return new PrimaryFailureAnalysis(null, null, List.of(), List.of(), Instant.now(), "Incident is null");
        }

        List<IncidentEvidence> evidence = incident.getEvidence();
        if ((evidence == null || evidence.isEmpty()) && evidenceRepository != null && incident.getId() != null) {
            evidence = evidenceRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
        }

        return analyzeEvidence(incident.getId(), evidence, incident.getPrimaryService());
    }

    /**
     * Analyze a list of incident evidence records.
     */
    public PrimaryFailureAnalysis analyzeEvidence(Long incidentId, List<IncidentEvidence> evidenceList, String defaultPrimaryService) {
        if (evidenceList == null || evidenceList.isEmpty()) {
            String fallback = defaultPrimaryService != null ? defaultPrimaryService : "unknown-service";
            PrimaryFailureCandidate emptyCandidate = new PrimaryFailureCandidate(
                    fallback, 50.0, "LOW", true, false, Instant.now(), Instant.now(),
                    0, "UNKNOWN", AnomalySeverity.MEDIUM, Map.of(),
                    List.of("No evidence events available for detailed analysis; defaulting to primary service"),
                    List.of()
            );
            return new PrimaryFailureAnalysis(incidentId, emptyCandidate, List.of(emptyCandidate), List.of(), Instant.now(),
                    "No evidence records available for incident " + incidentId);
        }

        // 1. Group evidence by service
        Map<String, List<IncidentEvidence>> serviceEvidence = new LinkedHashMap<>();
        for (IncidentEvidence ev : evidenceList) {
            String svc = ev.getService() != null ? ev.getService().toLowerCase().trim() : "unknown";
            serviceEvidence.computeIfAbsent(svc, k -> new ArrayList<>()).add(ev);
        }

        Set<String> failingServices = serviceEvidence.keySet();

        // 2. Global metrics
        Instant globalEarliest = evidenceList.stream()
                .map(IncidentEvidence::getTimestamp)
                .min(Instant::compareTo)
                .orElse(Instant.now());

        Instant globalLatest = evidenceList.stream()
                .map(IncidentEvidence::getTimestamp)
                .max(Instant::compareTo)
                .orElse(globalEarliest);

        long totalWindowSeconds = Math.max(1, Duration.between(globalEarliest, globalLatest).toSeconds());
        int totalEvents = evidenceList.size();

        // 3. Compute stats per service
        Map<String, ServiceStats> statsMap = new HashMap<>();
        for (Map.Entry<String, List<IncidentEvidence>> entry : serviceEvidence.entrySet()) {
            String svc = entry.getKey();
            List<IncidentEvidence> events = entry.getValue();

            Instant firstSeen = events.stream().map(IncidentEvidence::getTimestamp).min(Instant::compareTo).orElse(globalEarliest);
            Instant lastSeen = events.stream().map(IncidentEvidence::getTimestamp).max(Instant::compareTo).orElse(firstSeen);
            AnomalySeverity maxSev = events.stream()
                    .map(IncidentEvidence::getSeverity)
                    .max(Comparator.comparingInt(AnomalySeverity::ordinal))
                    .orElse(AnomalySeverity.MEDIUM);

            // Determine most common eventType
            Map<String, Long> typeCounts = new HashMap<>();
            for (IncidentEvidence ev : events) {
                typeCounts.put(ev.getEventType(), typeCounts.getOrDefault(ev.getEventType(), 0L) + 1L);
            }
            String primaryType = typeCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("ERROR");

            statsMap.put(svc, new ServiceStats(svc, firstSeen, lastSeen, events.size(), maxSev, primaryType));
        }

        // 4. Score each candidate service
        List<PrimaryFailureCandidate> candidates = new ArrayList<>();

        for (ServiceStats stats : statsMap.values()) {
            String svc = stats.service;
            List<String> reasons = new ArrayList<>();
            Map<String, Double> scoreBreakdown = new LinkedHashMap<>();

            // A. Temporal Precedence Score (0.0 to 40.0 pts)
            long delaySec = Duration.between(globalEarliest, stats.firstSeen).toSeconds();
            double temporalScore;
            if (delaySec <= 0) {
                temporalScore = 40.0;
                reasons.add(String.format("First observed failure in cascade (timestamp: %s)", stats.firstSeen));
            } else {
                double decay = Math.max(0.0, 1.0 - ((double) delaySec / (double) totalWindowSeconds));
                temporalScore = Math.max(0.0, 40.0 * decay);
                reasons.add(String.format("Observed %d seconds after cascade origin", delaySec));
            }
            scoreBreakdown.put("temporalScore", Math.round(temporalScore * 10.0) / 10.0);

            // B. Dependency Topology Position Score (0.0 to 30.0 pts)
            double topologyScore = 15.0; // neutral base

            Set<String> downstreamServices = getDownstreamServices(svc);
            Set<String> upstreamServices = getUpstreamServices(svc);

            Set<String> failingDownstream = new HashSet<>(downstreamServices);
            failingDownstream.retainAll(failingServices);

            Set<String> failingUpstream = new HashSet<>(upstreamServices);
            failingUpstream.retainAll(failingServices);

            if (failingDownstream.isEmpty()) {
                // S has no downstream dependencies that failed (is a leaf/root dependency)
                topologyScore += 15.0;
                reasons.add("Leaf dependency position: no downstream dependencies failed in this incident");
            } else {
                // S depends on another failing service
                // Check if any downstream failing service failed BEFORE S
                boolean downstreamFailedEarlier = failingDownstream.stream()
                        .anyMatch(ds -> statsMap.containsKey(ds) && statsMap.get(ds).firstSeen.isBefore(stats.firstSeen));
                if (downstreamFailedEarlier) {
                    topologyScore -= 10.0;
                    reasons.add(String.format("Downstream dependency (%s) failed prior to this service", String.join(", ", failingDownstream)));
                }
            }

            if (!failingUpstream.isEmpty()) {
                // Other failing services depend on S
                topologyScore += Math.min(5.0, failingUpstream.size() * 2.5);
                reasons.add(String.format("Depended upon by upstream caller(s): %s", String.join(", ", failingUpstream)));
            }

            topologyScore = Math.max(0.0, Math.min(30.0, topologyScore));
            scoreBreakdown.put("topologyScore", Math.round(topologyScore * 10.0) / 10.0);

            // C. Severity Score (0.0 to 20.0 pts)
            double severityScore = switch (stats.maxSeverity) {
                case CRITICAL -> 20.0;
                case HIGH -> 15.0;
                case MEDIUM -> 10.0;
                case LOW -> 5.0;
            };
            reasons.add(String.format("Maximum event severity: %s (+%.0f pts)", stats.maxSeverity, severityScore));
            scoreBreakdown.put("severityScore", severityScore);

            // D. Frequency / Error Concentration Score (0.0 to 10.0 pts)
            double concentrationRatio = (double) stats.eventCount / (double) totalEvents;
            double frequencyScore = Math.min(10.0, Math.round(concentrationRatio * 10.0 * 10.0) / 10.0);
            reasons.add(String.format("Error volume: %d/%d events (%.1f%%)", stats.eventCount, totalEvents, concentrationRatio * 100.0));
            scoreBreakdown.put("frequencyScore", frequencyScore);

            // Total Score
            double totalScore = Math.min(100.0, Math.max(0.0, temporalScore + topologyScore + severityScore + frequencyScore));
            totalScore = Math.round(totalScore * 10.0) / 10.0;

            candidates.add(new PrimaryFailureCandidate(
                    svc,
                    totalScore,
                    "CALCULATING",
                    false,
                    false,
                    stats.firstSeen,
                    stats.lastSeen,
                    stats.eventCount,
                    stats.primaryEventType,
                    stats.maxSeverity,
                    scoreBreakdown,
                    reasons,
                    new ArrayList<>()
            ));
        }

        // 5. Rank candidates by score descending
        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));

        PrimaryFailureCandidate top = candidates.getFirst();
        double secondScore = candidates.size() > 1 ? candidates.get(1).score() : 0.0;
        double gap = top.score() - secondScore;

        String confidence = "MEDIUM";
        if (top.score() >= 75.0 && (gap >= 10.0 || candidates.size() == 1)) {
            confidence = "HIGH";
        } else if (top.score() < 50.0 || gap < 5.0) {
            confidence = "LOW";
        }

        // 6. Partition primary vs symptoms
        List<String> symptomServices = new ArrayList<>();
        List<PrimaryFailureCandidate> finalizedCandidates = new ArrayList<>();
        List<PrimaryFailureCandidate> symptomCandidates = new ArrayList<>();

        for (int i = 0; i < candidates.size(); i++) {
            PrimaryFailureCandidate c = candidates.get(i);
            if (i == 0) {
                // Primary
                finalizedCandidates.add(new PrimaryFailureCandidate(
                        c.service(), c.score(), confidence, true, false,
                        c.firstSeen(), c.lastSeen(), c.eventCount(), c.primaryEventType(),
                        c.maxSeverity(), c.scoringBreakdown(), c.reasons(), symptomServices
                ));
            } else {
                // Symptom
                symptomServices.add(c.service());
                PrimaryFailureCandidate symptom = new PrimaryFailureCandidate(
                        c.service(), c.score(), confidence, false, true,
                        c.firstSeen(), c.lastSeen(), c.eventCount(), c.primaryEventType(),
                        c.maxSeverity(), c.scoringBreakdown(), c.reasons(), List.of()
                );
                finalizedCandidates.add(symptom);
                symptomCandidates.add(symptom);
            }
        }

        PrimaryFailureCandidate primary = finalizedCandidates.getFirst();

        String summary = String.format(
                "Primary failure identified as '%s' (confidence: %s, score: %.1f/100) due to %s. Downstream symptoms: %s",
                primary.service(),
                confidence,
                primary.score(),
                primary.reasons().isEmpty() ? "topological analysis" : primary.reasons().getFirst(),
                symptomServices.isEmpty() ? "none" : String.join(", ", symptomServices)
        );

        return new PrimaryFailureAnalysis(
                incidentId,
                primary,
                finalizedCandidates,
                symptomCandidates,
                Instant.now(),
                summary
        );
    }

    private Set<String> getDownstreamServices(String service) {
        if (dependencyService != null) {
            List<ServiceDependency> deps = dependencyService.getDownstream(service);
            Set<String> set = new HashSet<>();
            for (ServiceDependency d : deps) {
                set.add(d.getTargetService());
            }
            return set;
        }
        return Collections.emptySet();
    }

    private Set<String> getUpstreamServices(String service) {
        if (dependencyService != null) {
            List<ServiceDependency> deps = dependencyService.getUpstream(service);
            Set<String> set = new HashSet<>();
            for (ServiceDependency d : deps) {
                set.add(d.getSourceService());
            }
            return set;
        }
        return Collections.emptySet();
    }

    private record ServiceStats(
            String service,
            Instant firstSeen,
            Instant lastSeen,
            long eventCount,
            AnomalySeverity maxSeverity,
            String primaryEventType
    ) {}
}
