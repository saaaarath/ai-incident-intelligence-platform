package com.aiincident.logprocessor.incident;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentEvidenceRepository extends JpaRepository<IncidentEvidence, Long> {

    List<IncidentEvidence> findByIncidentIdOrderByTimestampAsc(Long incidentId);

    List<IncidentEvidence> findByIncidentId(Long incidentId);

    Optional<IncidentEvidence> findByEventId(String eventId);

    List<IncidentEvidence> findByTraceId(String traceId);

    List<IncidentEvidence> findByService(String service);

    List<IncidentEvidence> findByFingerprint(String fingerprint);

    void deleteByIncidentId(Long incidentId);
}
