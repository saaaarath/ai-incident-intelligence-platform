package com.aiincident.logprocessor.anomaly;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnomalyRepository extends JpaRepository<AnomalyEvent, Long> {

    List<AnomalyEvent> findByService(String service);

    List<AnomalyEvent> findByMetric(String metric);

    List<AnomalyEvent> findBySeverity(AnomalySeverity severity);

    List<AnomalyEvent> findByDetectedAtBetween(Instant from, Instant to);

    List<AnomalyEvent> findByServiceAndDetectedAtBetween(String service, Instant from, Instant to);

    List<AnomalyEvent> findByServiceAndMetric(String service, String metric);
}
