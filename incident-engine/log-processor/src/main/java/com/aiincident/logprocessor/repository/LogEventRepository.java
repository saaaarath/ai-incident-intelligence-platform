package com.aiincident.logprocessor.repository;

import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LogEventRepository extends JpaRepository<ProcessedLogEvent, Long> {

    Optional<ProcessedLogEvent> findByEventId(String eventId);

    List<ProcessedLogEvent> findByTraceId(String traceId);

    List<ProcessedLogEvent> findByService(String service);

    List<ProcessedLogEvent> findByEventType(String eventType);

    List<ProcessedLogEvent> findByLevel(String level);
}
