package com.aiincident.logprocessor.rca;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for RcaReportEntity.
 */
@Repository
public interface RcaReportRepository extends JpaRepository<RcaReportEntity, Long> {

    Optional<RcaReportEntity> findFirstByIncidentIdOrderByCreatedAtDesc(String incidentId);

    Optional<RcaReportEntity> findFirstByNumericIncidentIdOrderByCreatedAtDesc(Long numericIncidentId);

    List<RcaReportEntity> findByIncidentIdOrderByCreatedAtDesc(String incidentId);

    List<RcaReportEntity> findByNumericIncidentIdOrderByCreatedAtDesc(Long numericIncidentId);

    boolean existsByIncidentId(String incidentId);

    boolean existsByNumericIncidentId(Long numericIncidentId);
}
