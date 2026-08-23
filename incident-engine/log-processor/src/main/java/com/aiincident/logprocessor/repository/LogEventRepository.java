package com.aiincident.logprocessor.repository;

import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LogEventRepository extends JpaRepository<ProcessedLogEvent, Long> {

    Optional<ProcessedLogEvent> findByEventId(String eventId);

    List<ProcessedLogEvent> findByTraceId(String traceId);

    List<ProcessedLogEvent> findByService(String service);

    List<ProcessedLogEvent> findByEventType(String eventType);

    List<ProcessedLogEvent> findByLevel(String level);

    List<ProcessedLogEvent> findByTimestampBetween(Instant start, Instant end);

    List<ProcessedLogEvent> findByTimestampBetweenOrderByTimestampAsc(Instant start, Instant end);

    List<ProcessedLogEvent> findByServiceAndTimestampBetween(String service, Instant start, Instant end);

    List<ProcessedLogEvent> findByTimestampGreaterThanEqual(Instant start);

    @Query("SELECT DISTINCT l.service FROM ProcessedLogEvent l WHERE l.service IS NOT NULL ORDER BY l.service ASC")
    List<String> findDistinctServices();
}
