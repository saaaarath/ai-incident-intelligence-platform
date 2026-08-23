package com.aiincident.logprocessor.incident;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByIncidentId(String incidentId);

    List<Incident> findByStatus(IncidentStatus status);

    List<Incident> findByPrimaryService(String primaryService);

    List<Incident> findByPrimaryServiceAndStatusIn(String primaryService, Collection<IncidentStatus> statuses);

    Optional<Incident> findFirstByPrimaryServiceAndStatusInOrderByStartedAtDesc(
            String primaryService,
            Collection<IncidentStatus> statuses
    );

    List<Incident> findBySeverity(com.aiincident.logprocessor.anomaly.AnomalySeverity severity);

    List<Incident> findByPrimaryServiceAndStatus(String primaryService, IncidentStatus status);

    List<Incident> findByPrimaryServiceAndSeverity(String primaryService, com.aiincident.logprocessor.anomaly.AnomalySeverity severity);

    List<Incident> findByStatusAndSeverity(IncidentStatus status, com.aiincident.logprocessor.anomaly.AnomalySeverity severity);

    List<Incident> findByDetectedAtBetween(Instant from, Instant to);
}
