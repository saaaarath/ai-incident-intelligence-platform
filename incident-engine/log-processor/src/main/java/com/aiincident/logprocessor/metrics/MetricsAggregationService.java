package com.aiincident.logprocessor.metrics;

import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.repository.LogEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricsAggregationService {

    private static final Logger log = LoggerFactory.getLogger(MetricsAggregationService.class);

    private static final Set<String> LATENCY_KEYS = Set.of(
            "latency",
            "latencyms",
            "duration",
            "durationms",
            "responsetime",
            "responsetimems",
            "executiontime",
            "executiontimems",
            "response_time_ms",
            "latency_ms",
            "duration_ms"
    );

    private final LogEventRepository logEventRepository;
    private final ObjectMapper objectMapper;
    private final MetricsAggregationProperties properties;

    public MetricsAggregationService(
            LogEventRepository logEventRepository,
            ObjectMapper objectMapper,
            MetricsAggregationProperties properties) {
        this.logEventRepository = logEventRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Calculate windowed operational metrics for a specific service or all services.
     */
    @Transactional(readOnly = true)
    public List<OperationalMetrics> getMetrics(String service, Instant from, Instant to, Duration windowDuration) {
        Instant effectiveTo = (to != null) ? to : Instant.now();
        Duration effectiveWindow = (windowDuration != null && !windowDuration.isNegative() && !windowDuration.isZero())
                ? windowDuration
                : properties.getDefaultWindowDuration();
        Instant effectiveFrom = (from != null) ? from : effectiveTo.minus(Duration.ofHours(1));

        if (effectiveFrom.isAfter(effectiveTo)) {
            Instant temp = effectiveFrom;
            effectiveFrom = effectiveTo;
            effectiveTo = temp;
        }

        List<ProcessedLogEvent> events;
        if (service != null && !service.isBlank()) {
            events = logEventRepository.findByServiceAndTimestampBetween(service.trim(), effectiveFrom, effectiveTo);
        } else {
            events = logEventRepository.findByTimestampBetween(effectiveFrom, effectiveTo);
        }

        return aggregateEventsIntoWindows(events, effectiveWindow, service);
    }

    /**
     * Calculate summary metrics across an entire time range as a single window.
     */
    @Transactional(readOnly = true)
    public OperationalMetrics getSummary(String service, Instant from, Instant to) {
        Instant effectiveTo = (to != null) ? to : Instant.now();
        Instant effectiveFrom = (from != null) ? from : effectiveTo.minus(Duration.ofHours(1));

        if (effectiveFrom.isAfter(effectiveTo)) {
            Instant temp = effectiveFrom;
            effectiveFrom = effectiveTo;
            effectiveTo = temp;
        }

        List<ProcessedLogEvent> events;
        String serviceName = (service != null && !service.isBlank()) ? service.trim() : "all-services";
        if (service != null && !service.isBlank()) {
            events = logEventRepository.findByServiceAndTimestampBetween(serviceName, effectiveFrom, effectiveTo);
        } else {
            events = logEventRepository.findByTimestampBetween(effectiveFrom, effectiveTo);
        }

        return computeMetrics(serviceName, effectiveFrom, effectiveTo, events);
    }

    /**
     * Get list of distinct service names available in stored operational logs.
     */
    @Transactional(readOnly = true)
    public List<String> getServices() {
        return logEventRepository.findDistinctServices();
    }

    /**
     * Aggregate a collection of log events into time-window buckets per service.
     */
    public List<OperationalMetrics> aggregateEventsIntoWindows(
            List<ProcessedLogEvent> events,
            Duration windowDuration,
            String targetService) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        long windowSeconds = Math.max(1, windowDuration.toSeconds());

        // Group by service -> windowStart -> list of events
        Map<String, Map<Instant, List<ProcessedLogEvent>>> grouped = new HashMap<>();

        for (ProcessedLogEvent event : events) {
            String serviceName = event.getService() != null ? event.getService().trim() : "unknown";
            if (targetService != null && !targetService.isBlank() && !targetService.trim().equalsIgnoreCase(serviceName)) {
                continue;
            }

            Instant eventTime = event.getTimestamp() != null ? event.getTimestamp() : event.getReceivedAt();
            if (eventTime == null) {
                continue;
            }

            long epochSecond = eventTime.getEpochSecond();
            long bucketStartEpochSecond = (epochSecond / windowSeconds) * windowSeconds;
            Instant bucketStart = Instant.ofEpochSecond(bucketStartEpochSecond);

            grouped.computeIfAbsent(serviceName, k -> new TreeMap<>())
                    .computeIfAbsent(bucketStart, k -> new ArrayList<>())
                    .add(event);
        }

        List<OperationalMetrics> results = new ArrayList<>();

        for (Map.Entry<String, Map<Instant, List<ProcessedLogEvent>>> serviceEntry : grouped.entrySet()) {
            String service = serviceEntry.getKey();
            Map<Instant, List<ProcessedLogEvent>> timeBuckets = serviceEntry.getValue();

            for (Map.Entry<Instant, List<ProcessedLogEvent>> bucketEntry : timeBuckets.entrySet()) {
                Instant windowStart = bucketEntry.getKey();
                Instant windowEnd = windowStart.plus(windowDuration);
                List<ProcessedLogEvent> windowEvents = bucketEntry.getValue();

                results.add(computeMetrics(service, windowStart, windowEnd, windowEvents));
            }
        }

        results.sort(Comparator.comparing(OperationalMetrics::windowStart)
                .thenComparing(OperationalMetrics::service));

        return results;
    }

    /**
     * Compute operational metrics from a list of events belonging to a specific window.
     */
    public OperationalMetrics computeMetrics(
            String service,
            Instant windowStart,
            Instant windowEnd,
            List<ProcessedLogEvent> events) {
        if (events == null || events.isEmpty()) {
            return new OperationalMetrics(
                    service,
                    windowStart,
                    windowEnd,
                    0,
                    0,
                    0.0,
                    LatencyMetrics.empty()
            );
        }

        long totalEvents = events.size();
        long errorCount = events.stream()
                .filter(e -> e.getLevel() != null && "ERROR".equalsIgnoreCase(e.getLevel().trim()))
                .count();

        double errorRate = totalEvents > 0 ? (double) errorCount / (double) totalEvents : 0.0;
        // Format to 4 decimal places precision for cleanliness
        errorRate = Math.round(errorRate * 10000.0) / 10000.0;

        List<Double> latencies = new ArrayList<>();
        for (ProcessedLogEvent event : events) {
            Double latency = extractLatency(event.getMetadata());
            if (latency != null && latency >= 0.0) {
                latencies.add(latency);
            }
        }

        LatencyMetrics latencyMetrics;
        if (latencies.isEmpty()) {
            latencyMetrics = LatencyMetrics.empty();
        } else {
            Collections.sort(latencies);
            long count = latencies.size();
            double min = latencies.getFirst();
            double max = latencies.getLast();
            double avg = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double p50 = calculatePercentile(latencies, 50.0);
            double p95 = calculatePercentile(latencies, 95.0);
            double p99 = calculatePercentile(latencies, 99.0);

            latencyMetrics = new LatencyMetrics(
                    count,
                    round(min, 2),
                    round(max, 2),
                    round(avg, 2),
                    round(p50, 2),
                    round(p95, 2),
                    round(p99, 2)
            );
        }

        return new OperationalMetrics(
                service,
                windowStart,
                windowEnd,
                totalEvents,
                errorCount,
                errorRate,
                latencyMetrics
        );
    }

    /**
     * Safely extract latency numeric value from JSON metadata string.
     */
    public Double extractLatency(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            if (!node.isObject()) {
                return null;
            }

            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String keyNormalized = entry.getKey().toLowerCase().replace("-", "").replace("_", "");
                if (LATENCY_KEYS.contains(keyNormalized) || keyNormalized.contains("latency") || keyNormalized.contains("duration")) {
                    JsonNode valueNode = entry.getValue();
                    if (valueNode.isNumber()) {
                        return valueNode.asDouble();
                    } else if (valueNode.isTextual()) {
                        try {
                            return Double.parseDouble(valueNode.asText().trim().replaceAll("[^0-9.]", ""));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.trace("Could not parse metadata for latency extraction: {}", metadataJson);
        }

        return null;
    }

    /**
     * Calculate percentile using linear interpolation between nearest ranks.
     */
    public static double calculatePercentile(List<Double> sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0.0;
        }
        if (sortedValues.size() == 1 || percentile <= 0.0) {
            return sortedValues.getFirst();
        }
        if (percentile >= 100.0) {
            return sortedValues.getLast();
        }

        double rank = (percentile / 100.0) * (sortedValues.size() - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) {
            return sortedValues.get(lower);
        }

        double weight = rank - lower;
        return sortedValues.get(lower) * (1.0 - weight) + sortedValues.get(upper) * weight;
    }

    private static double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }
}
