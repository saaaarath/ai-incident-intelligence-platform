package com.aiincident.logprocessor.metrics;

import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.repository.LogEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsAggregationServiceTest {

    @Mock
    private LogEventRepository logEventRepository;

    private ObjectMapper objectMapper;
    private MetricsAggregationProperties properties;
    private MetricsAggregationService metricsService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new MetricsAggregationProperties();
        properties.setDefaultWindowMinutes(1);
        properties.setDefaultWindowSeconds(60);
        metricsService = new MetricsAggregationService(logEventRepository, objectMapper, properties);
    }

    @Test
    @DisplayName("Should correctly calculate metrics per service and 1-minute time windows")
    void testMetricsCalculationFixedWindows() {
        Instant baseTime = Instant.parse("2026-08-23T10:00:00Z");

        // Window 1: 10:00:00 - 10:01:00 (5 events: 4 INFO, 1 ERROR, all with latency)
        ProcessedLogEvent e1 = createEvent("e1", "order-service", "INFO", "ORDER_CREATED", baseTime.plusSeconds(5), "{\"durationMs\": 50}");
        ProcessedLogEvent e2 = createEvent("e2", "order-service", "INFO", "ORDER_CREATED", baseTime.plusSeconds(15), "{\"latency\": 100}");
        ProcessedLogEvent e3 = createEvent("e3", "order-service", "INFO", "ORDER_CREATED", baseTime.plusSeconds(25), "{\"responseTime\": 150}");
        ProcessedLogEvent e4 = createEvent("e4", "order-service", "INFO", "ORDER_CREATED", baseTime.plusSeconds(35), "{\"duration\": 200}");
        ProcessedLogEvent e5 = createEvent("e5", "order-service", "ERROR", "PAYMENT_FAILED", baseTime.plusSeconds(45), "{\"latencyMs\": 300}");

        // Window 2: 10:01:00 - 10:02:00 (3 events: 3 INFO, no latency)
        ProcessedLogEvent e6 = createEvent("e6", "order-service", "INFO", "ORDER_CREATED", baseTime.plusSeconds(65), "{}");
        ProcessedLogEvent e7 = createEvent("e7", "order-service", "INFO", "ORDER_CREATED", baseTime.plusSeconds(75), null);
        ProcessedLogEvent e8 = createEvent("e8", "order-service", "INFO", "ORDER_CREATED", baseTime.plusSeconds(85), "{\"orderId\": 100}");

        // Window 3: 10:02:00 - 10:03:00 (2 events: 2 ERROR, 100% error rate)
        ProcessedLogEvent e9 = createEvent("e9", "order-service", "ERROR", "DB_TIMEOUT", baseTime.plusSeconds(130), "{\"latency\": 500}");
        ProcessedLogEvent e10 = createEvent("e10", "order-service", "ERROR", "SERVICE_UNAVAILABLE", baseTime.plusSeconds(140), "{\"latency\": 1000}");

        List<ProcessedLogEvent> allEvents = List.of(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10);
        when(logEventRepository.findByServiceAndTimestampBetween(eq("order-service"), any(), any()))
                .thenReturn(allEvents);

        List<OperationalMetrics> result = metricsService.getMetrics(
                "order-service",
                baseTime,
                baseTime.plusSeconds(180),
                Duration.ofMinutes(1)
        );

        assertThat(result).hasSize(3);

        // Verify Window 1
        OperationalMetrics w1 = result.get(0);
        assertThat(w1.service()).isEqualTo("order-service");
        assertThat(w1.windowStart()).isEqualTo(baseTime);
        assertThat(w1.windowEnd()).isEqualTo(baseTime.plusSeconds(60));
        assertThat(w1.totalEvents()).isEqualTo(5);
        assertThat(w1.errorCount()).isEqualTo(1);
        assertThat(w1.errorRate()).isEqualTo(0.2); // 1 / 5 = 20%
        assertThat(w1.latency().count()).isEqualTo(5);
        assertThat(w1.latency().min()).isEqualTo(50.0);
        assertThat(w1.latency().max()).isEqualTo(300.0);
        assertThat(w1.latency().avg()).isEqualTo(160.0);
        assertThat(w1.latency().p50()).isEqualTo(150.0);

        // Verify Window 2 (no latency metadata)
        OperationalMetrics w2 = result.get(1);
        assertThat(w2.service()).isEqualTo("order-service");
        assertThat(w2.windowStart()).isEqualTo(baseTime.plusSeconds(60));
        assertThat(w2.windowEnd()).isEqualTo(baseTime.plusSeconds(120));
        assertThat(w2.totalEvents()).isEqualTo(3);
        assertThat(w2.errorCount()).isEqualTo(0);
        assertThat(w2.errorRate()).isEqualTo(0.0);
        assertThat(w2.latency().count()).isEqualTo(0);
        assertThat(w2.latency().min()).isNull();
        assertThat(w2.latency().avg()).isNull();

        // Verify Window 3 (100% errors)
        OperationalMetrics w3 = result.get(2);
        assertThat(w3.service()).isEqualTo("order-service");
        assertThat(w3.windowStart()).isEqualTo(baseTime.plusSeconds(120));
        assertThat(w3.windowEnd()).isEqualTo(baseTime.plusSeconds(180));
        assertThat(w3.totalEvents()).isEqualTo(2);
        assertThat(w3.errorCount()).isEqualTo(2);
        assertThat(w3.errorRate()).isEqualTo(1.0); // 100%
        assertThat(w3.latency().count()).isEqualTo(2);
        assertThat(w3.latency().min()).isEqualTo(500.0);
        assertThat(w3.latency().max()).isEqualTo(1000.0);
        assertThat(w3.latency().avg()).isEqualTo(750.0);
    }

    @Test
    @DisplayName("Should separate metrics by service across windows")
    void testMultiServiceAggregation() {
        Instant baseTime = Instant.parse("2026-08-23T10:00:00Z");

        ProcessedLogEvent o1 = createEvent("o1", "order-service", "INFO", "ORDER_CREATED", baseTime.plusSeconds(10), "{\"latency\": 80}");
        ProcessedLogEvent p1 = createEvent("p1", "payment-service", "ERROR", "PAYMENT_FAILED", baseTime.plusSeconds(20), "{\"latency\": 250}");
        ProcessedLogEvent i1 = createEvent("i1", "inventory-service", "INFO", "INVENTORY_RESERVED", baseTime.plusSeconds(30), "{\"latency\": 40}");

        when(logEventRepository.findByTimestampBetween(any(), any()))
                .thenReturn(List.of(o1, p1, i1));

        List<OperationalMetrics> result = metricsService.getMetrics(
                null,
                baseTime,
                baseTime.plusSeconds(60),
                Duration.ofMinutes(1)
        );

        assertThat(result).hasSize(3);

        OperationalMetrics inventoryMetrics = result.stream()
                .filter(m -> m.service().equals("inventory-service")).findFirst().orElseThrow();
        assertThat(inventoryMetrics.totalEvents()).isEqualTo(1);
        assertThat(inventoryMetrics.errorCount()).isEqualTo(0);
        assertThat(inventoryMetrics.errorRate()).isEqualTo(0.0);
        assertThat(inventoryMetrics.latency().avg()).isEqualTo(40.0);

        OperationalMetrics paymentMetrics = result.stream()
                .filter(m -> m.service().equals("payment-service")).findFirst().orElseThrow();
        assertThat(paymentMetrics.totalEvents()).isEqualTo(1);
        assertThat(paymentMetrics.errorCount()).isEqualTo(1);
        assertThat(paymentMetrics.errorRate()).isEqualTo(1.0);
        assertThat(paymentMetrics.latency().avg()).isEqualTo(250.0);

        OperationalMetrics orderMetrics = result.stream()
                .filter(m -> m.service().equals("order-service")).findFirst().orElseThrow();
        assertThat(orderMetrics.totalEvents()).isEqualTo(1);
        assertThat(orderMetrics.errorCount()).isEqualTo(0);
        assertThat(orderMetrics.errorRate()).isEqualTo(0.0);
        assertThat(orderMetrics.latency().avg()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("Should accurately calculate percentiles with deterministic data")
    void testPercentileCalculation() {
        List<Double> data = List.of(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0);

        double p50 = MetricsAggregationService.calculatePercentile(data, 50.0);
        double p95 = MetricsAggregationService.calculatePercentile(data, 95.0);
        double p99 = MetricsAggregationService.calculatePercentile(data, 99.0);

        assertThat(p50).isCloseTo(55.0, within(0.01));
        assertThat(p95).isCloseTo(95.5, within(0.01));
        assertThat(p99).isCloseTo(99.1, within(0.01));
    }

    @Test
    @DisplayName("Should calculate summary metric across single custom range")
    void testSummaryCalculation() {
        Instant from = Instant.parse("2026-08-23T10:00:00Z");
        Instant to = Instant.parse("2026-08-23T10:30:00Z");

        ProcessedLogEvent e1 = createEvent("e1", "order-service", "INFO", "ORDER_CREATED", from.plusSeconds(100), "{\"latency\": 100}");
        ProcessedLogEvent e2 = createEvent("e2", "order-service", "ERROR", "PAYMENT_FAILED", from.plusSeconds(500), "{\"latency\": 200}");
        ProcessedLogEvent e3 = createEvent("e3", "order-service", "INFO", "ORDER_CREATED", from.plusSeconds(900), "{\"latency\": 300}");

        when(logEventRepository.findByServiceAndTimestampBetween("order-service", from, to))
                .thenReturn(List.of(e1, e2, e3));

        OperationalMetrics summary = metricsService.getSummary("order-service", from, to);

        assertThat(summary.service()).isEqualTo("order-service");
        assertThat(summary.windowStart()).isEqualTo(from);
        assertThat(summary.windowEnd()).isEqualTo(to);
        assertThat(summary.totalEvents()).isEqualTo(3);
        assertThat(summary.errorCount()).isEqualTo(1);
        assertThat(summary.errorRate()).isCloseTo(0.3333, within(0.001));
        assertThat(summary.latency().count()).isEqualTo(3);
        assertThat(summary.latency().min()).isEqualTo(100.0);
        assertThat(summary.latency().max()).isEqualTo(300.0);
        assertThat(summary.latency().avg()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("Should extract latency from various metadata field formats safely")
    void testLatencyExtractionFormats() {
        assertThat(metricsService.extractLatency("{\"latency\": 120.5}")).isEqualTo(120.5);
        assertThat(metricsService.extractLatency("{\"latencyMs\": 85}")).isEqualTo(85.0);
        assertThat(metricsService.extractLatency("{\"duration_ms\": 45}")).isEqualTo(45.0);
        assertThat(metricsService.extractLatency("{\"responseTime\": \"250ms\"}")).isEqualTo(250.0);
        assertThat(metricsService.extractLatency("{\"randomKey\": \"noLatency\"}")).isNull();
        assertThat(metricsService.extractLatency("{invalidJson")).isNull();
        assertThat(metricsService.extractLatency(null)).isNull();
    }

    private ProcessedLogEvent createEvent(
            String eventId,
            String service,
            String level,
            String eventType,
            Instant timestamp,
            String metadata) {
        return new ProcessedLogEvent(
                eventId,
                timestamp,
                service,
                level,
                eventType,
                "trace-" + eventId,
                "Test message " + eventId,
                metadata,
                timestamp
        );
    }
}
