package com.aiincident.logprocessor.historical;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostmortemRepository extends JpaRepository<Postmortem, Long> {

    Optional<Postmortem> findByPostmortemId(String postmortemId);

    Optional<Postmortem> findByIncidentId(String incidentId);

    List<Postmortem> findByCategory(HistoricalIncidentCategory category);

    @Query("SELECT DISTINCT p FROM Postmortem p " +
           "LEFT JOIN p.actionItems a " +
           "LEFT JOIN p.lessonsLearned l " +
           "WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(p.incidentId) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(p.executiveSummary) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(p.rootCauseAnalysis) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(p.impactSummary) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(a) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(l) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Postmortem> searchPostmortems(@Param("query") String query);
}
