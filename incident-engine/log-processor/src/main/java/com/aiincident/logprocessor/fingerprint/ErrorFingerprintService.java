package com.aiincident.logprocessor.fingerprint;

import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.incident.EventTypeClassifier;
import com.aiincident.logprocessor.repository.LogEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing, aggregating, and querying error fingerprints across operational logs.
 */
@Service
public class ErrorFingerprintService {

    private final ErrorFingerprintGenerator generator;
    private final LogEventRepository logEventRepository;
    private final EventTypeClassifier eventTypeClassifier;

    public ErrorFingerprintService(
            ErrorFingerprintGenerator generator,
            LogEventRepository logEventRepository,
            EventTypeClassifier eventTypeClassifier) {
        this.generator = generator;
        this.logEventRepository = logEventRepository;
        this.eventTypeClassifier = eventTypeClassifier;
    }

    public ErrorFingerprint generateFingerprint(String service, String eventType, String message) {
        return generator.generateFingerprint(service, eventType, message);
    }

    public ErrorFingerprint generateFingerprintForLog(ProcessedLogEvent event) {
        if (event == null) {
            return generator.generateFingerprint("unknown", "UNKNOWN", "");
        }
        return generator.generateFingerprint(event.getService(), event.getEventType(), event.getMessage());
    }

    /**
     * Group a list of log events by their calculated error fingerprint.
     */
    public Map<String, List<ProcessedLogEvent>> groupEventsByFingerprint(List<ProcessedLogEvent> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<ProcessedLogEvent>> groups = new LinkedHashMap<>();
        for (ProcessedLogEvent event : events) {
            ErrorFingerprint fp = generateFingerprintForLog(event);
            groups.computeIfAbsent(fp.fingerprintHash(), k -> new ArrayList<>()).add(event);
        }
        return groups;
    }

    /**
     * Generate summary statistics of error fingerprints over a time range.
     */
    @Transactional(readOnly = true)
    public List<FingerprintSummary> getFingerprintSummaries(Instant from, Instant to, String service) {
        Instant startTime = from != null ? from : Instant.now().minus(Duration.ofHours(24));
        Instant endTime = to != null ? to : Instant.now().plus(Duration.ofMinutes(5));

        List<ProcessedLogEvent> logs;
        if (service != null && !service.isBlank()) {
            logs = logEventRepository.findByServiceAndTimestampBetween(service.trim(), startTime, endTime);
        } else {
            logs = logEventRepository.findByTimestampBetweenOrderByTimestampAsc(startTime, endTime);
        }

        // Filter only failure events
        List<ProcessedLogEvent> failureEvents = logs.stream()
                .filter(l -> eventTypeClassifier.isFailureEvent(l.getLevel(), l.getEventType(), l.getMessage()))
                .toList();

        Map<String, List<ProcessedLogEvent>> grouped = groupEventsByFingerprint(failureEvents);
        List<FingerprintSummary> summaries = new ArrayList<>();

        for (Map.Entry<String, List<ProcessedLogEvent>> entry : grouped.entrySet()) {
            List<ProcessedLogEvent> eventList = entry.getValue();
            if (eventList.isEmpty()) continue;

            ProcessedLogEvent sample = eventList.getFirst();
            ErrorFingerprint fp = generateFingerprintForLog(sample);

            Instant firstSeen = eventList.stream().map(ProcessedLogEvent::getTimestamp).min(Instant::compareTo).orElse(startTime);
            Instant lastSeen = eventList.stream().map(ProcessedLogEvent::getTimestamp).max(Instant::compareTo).orElse(endTime);
            List<String> sampleIds = eventList.stream().map(ProcessedLogEvent::getEventId).limit(5).toList();

            summaries.add(new FingerprintSummary(
                    fp.fingerprintHash(),
                    fp.service(),
                    fp.eventType(),
                    fp.normalizedMessage(),
                    eventList.size(),
                    firstSeen,
                    lastSeen,
                    sampleIds
            ));
        }

        // Sort by occurrence count descending
        summaries.sort((a, b) -> Long.compare(b.count(), a.count()));
        return summaries;
    }

    public record FingerprintSummary(
            String fingerprintHash,
            String service,
            String eventType,
            String normalizedMessage,
            long count,
            Instant firstSeen,
            Instant lastSeen,
            List<String> sampleEventIds
    ) {}
}
