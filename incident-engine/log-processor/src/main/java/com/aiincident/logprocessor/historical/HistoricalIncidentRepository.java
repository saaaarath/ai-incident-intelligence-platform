package com.aiincident.logprocessor.historical;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HistoricalIncidentRepository extends JpaRepository<HistoricalIncident, Long> {

    Optional<HistoricalIncident> findByIncidentId(String incidentId);

    List<HistoricalIncident> findByCategory(HistoricalIncidentCategory category);

    @Query("SELECT DISTINCT h FROM HistoricalIncident h JOIN h.affectedServices s WHERE LOWER(s) = LOWER(:service)")
    List<HistoricalIncident> findByAffectedService(@Param("service") String service);

    @Query("SELECT DISTINCT h FROM HistoricalIncident h JOIN h.affectedServices s WHERE LOWER(s) = LOWER(:service) AND h.category = :category")
    List<HistoricalIncident> findByAffectedServiceAndCategory(@Param("service") String service, @Param("category") HistoricalIncidentCategory category);

    @Query("SELECT DISTINCT h FROM HistoricalIncident h " +
           "LEFT JOIN h.symptoms s " +
           "WHERE LOWER(h.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(h.rootCause) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(h.resolution) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(h.prevention) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(s) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<HistoricalIncident> searchIncidents(@Param("query") String query);
}
