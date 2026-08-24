package com.aiincident.logprocessor.historical;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RunbookRepository extends JpaRepository<Runbook, Long> {

    Optional<Runbook> findByRunbookId(String runbookId);

    List<Runbook> findByCategory(HistoricalIncidentCategory category);

    @Query("SELECT DISTINCT r FROM Runbook r JOIN r.applicableServices s WHERE LOWER(s) = LOWER(:service)")
    List<Runbook> findByApplicableService(@Param("service") String service);

    @Query("SELECT DISTINCT r FROM Runbook r JOIN r.applicableServices s WHERE LOWER(s) = LOWER(:service) AND r.category = :category")
    List<Runbook> findByApplicableServiceAndCategory(@Param("service") String service, @Param("category") HistoricalIncidentCategory category);

    @Query("SELECT DISTINCT r FROM Runbook r " +
           "LEFT JOIN r.triggerSymptoms s " +
           "LEFT JOIN r.tags t " +
           "WHERE LOWER(r.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(r.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(r.escalationPath) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(s) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(t) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Runbook> searchRunbooks(@Param("query") String query);
}
