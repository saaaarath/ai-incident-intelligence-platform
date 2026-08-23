package com.aiincident.logprocessor.repository;

import com.aiincident.logprocessor.entity.ProcessedDeploymentEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentEventRepository extends JpaRepository<ProcessedDeploymentEvent, Long> {

    Optional<ProcessedDeploymentEvent> findByEventId(String eventId);

    List<ProcessedDeploymentEvent> findByService(String service);

    List<ProcessedDeploymentEvent> findByServiceOrderByTimestampDesc(String service);

    List<ProcessedDeploymentEvent> findByVersion(String version);

    List<ProcessedDeploymentEvent> findByEventType(String eventType);

    List<ProcessedDeploymentEvent> findByTraceId(String traceId);

    List<ProcessedDeploymentEvent> findByTimestampBetweenOrderByTimestampAsc(java.time.Instant start, java.time.Instant end);
}
