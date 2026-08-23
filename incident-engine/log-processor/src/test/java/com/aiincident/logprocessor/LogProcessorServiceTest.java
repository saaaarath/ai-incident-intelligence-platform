package com.aiincident.logprocessor;

import com.aiincident.logging.LogEvent;
import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.repository.LogEventRepository;
import com.aiincident.logprocessor.service.LogProcessorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LogProcessorServiceTest {

    @Autowired
    private LogEventRepository logEventRepository;

    private LogProcessorService logProcessorService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        logProcessorService = new LogProcessorService(logEventRepository, objectMapper);
        logEventRepository.deleteAll();
    }

    @Test
    @DisplayName("Should successfully process and persist valid LogEvent")
    void testProcessValidLogEvent() {
        String eventId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        LogEvent event = new LogEvent(
                eventId,
                Instant.now(),
                "order-service",
                "INFO",
                "ORDER_CREATED",
                traceId,
                "Order created successfully",
                Map.of("orderId", 123, "customerId", "cust-1")
        );

        Optional<ProcessedLogEvent> result = logProcessorService.processEvent(event);

        assertThat(result).isPresent();
        ProcessedLogEvent saved = result.get();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getService()).isEqualTo("order-service");
        assertThat(saved.getLevel()).isEqualTo("INFO");
        assertThat(saved.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(saved.getTraceId()).isEqualTo(traceId);
        assertThat(saved.getMessage()).isEqualTo("Order created successfully");
        assertThat(saved.getMetadata()).contains("orderId");
        assertThat(saved.getReceivedAt()).isNotNull();

        assertThat(logEventRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Should successfully process valid JSON string")
    void testProcessValidRawJson() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        LogEvent event = new LogEvent(
                eventId,
                Instant.now(),
                "payment-service",
                "INFO",
                "PAYMENT_CREATED",
                traceId,
                "Payment processed",
                Map.of("amount", 99.99)
        );
        String json = objectMapper.writeValueAsString(event);

        Optional<ProcessedLogEvent> result = logProcessorService.processRawMessage(json);

        assertThat(result).isPresent();
        assertThat(result.get().getEventId()).isEqualTo(eventId);
        assertThat(result.get().getService()).isEqualTo("payment-service");
        assertThat(result.get().getEventType()).isEqualTo("PAYMENT_CREATED");
        assertThat(logEventRepository.findByTraceId(traceId)).hasSize(1);
    }

    @Test
    @DisplayName("Should safely reject malformed JSON without throwing exception")
    void testRejectMalformedJson() {
        assertThat(logProcessorService.processRawMessage("{broken-json: true,")).isEmpty();
        assertThat(logProcessorService.processRawMessage("not-even-json")).isEmpty();
        assertThat(logProcessorService.processRawMessage("")).isEmpty();
        assertThat(logProcessorService.processRawMessage("   ")).isEmpty();
        assertThat(logProcessorService.processRawMessage(null)).isEmpty();

        assertThat(logEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should safely reject events with missing required fields")
    void testRejectMissingRequiredFields() {
        Instant now = Instant.now();
        String traceId = UUID.randomUUID().toString();

        // Missing eventId
        LogEvent missingEventId = new LogEvent(null, now, "order-service", "INFO", "ORDER_CREATED", traceId, "msg", Map.of());
        assertThat(logProcessorService.processEvent(missingEventId)).isEmpty();

        // Blank eventId
        LogEvent blankEventId = new LogEvent("   ", now, "order-service", "INFO", "ORDER_CREATED", traceId, "msg", Map.of());
        assertThat(logProcessorService.processEvent(blankEventId)).isEmpty();

        // Missing timestamp
        LogEvent missingTimestamp = new LogEvent("evt-1", null, "order-service", "INFO", "ORDER_CREATED", traceId, "msg", Map.of());
        assertThat(logProcessorService.processEvent(missingTimestamp)).isEmpty();

        // Missing service
        LogEvent missingService = new LogEvent("evt-2", now, null, "INFO", "ORDER_CREATED", traceId, "msg", Map.of());
        assertThat(logProcessorService.processEvent(missingService)).isEmpty();

        // Missing level
        LogEvent missingLevel = new LogEvent("evt-3", now, "order-service", null, "ORDER_CREATED", traceId, "msg", Map.of());
        assertThat(logProcessorService.processEvent(missingLevel)).isEmpty();

        // Missing eventType
        LogEvent missingEventType = new LogEvent("evt-4", now, "order-service", "INFO", null, traceId, "msg", Map.of());
        assertThat(logProcessorService.processEvent(missingEventType)).isEmpty();

        // Missing traceId
        LogEvent missingTraceId = new LogEvent("evt-5", now, "order-service", "INFO", "ORDER_CREATED", null, "msg", Map.of());
        assertThat(logProcessorService.processEvent(missingTraceId)).isEmpty();

        // Missing message
        LogEvent missingMessage = new LogEvent("evt-6", now, "order-service", "INFO", "ORDER_CREATED", traceId, null, Map.of());
        assertThat(logProcessorService.processEvent(missingMessage)).isEmpty();

        // Blank message
        LogEvent blankMessage = new LogEvent("evt-7", now, "order-service", "INFO", "ORDER_CREATED", traceId, "  ", Map.of());
        assertThat(logProcessorService.processEvent(blankMessage)).isEmpty();

        assertThat(logEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should persist multiple events and support query by traceId, service, eventType")
    void testPersistenceAndQueries() {
        String trace1 = "trace-alpha";
        String trace2 = "trace-beta";

        LogEvent event1 = new LogEvent("e1", Instant.now(), "order-service", "INFO", "ORDER_CREATED", trace1, "Order 1", Map.of());
        LogEvent event2 = new LogEvent("e2", Instant.now(), "payment-service", "INFO", "PAYMENT_CREATED", trace1, "Payment 1", Map.of());
        LogEvent event3 = new LogEvent("e3", Instant.now(), "inventory-service", "INFO", "INVENTORY_RESERVED", trace1, "Inventory 1", Map.of());
        LogEvent event4 = new LogEvent("e4", Instant.now(), "order-service", "ERROR", "SERVICE_UNAVAILABLE", trace2, "Downstream failure", Map.of());

        logProcessorService.processEvent(event1);
        logProcessorService.processEvent(event2);
        logProcessorService.processEvent(event3);
        logProcessorService.processEvent(event4);

        assertThat(logEventRepository.count()).isEqualTo(4);

        List<ProcessedLogEvent> trace1Logs = logProcessorService.findByTraceId(trace1);
        assertThat(trace1Logs).hasSize(3);

        List<ProcessedLogEvent> orderLogs = logProcessorService.findByService("order-service");
        assertThat(orderLogs).hasSize(2);

        List<ProcessedLogEvent> errorLogs = logEventRepository.findByLevel("ERROR");
        assertThat(errorLogs).hasSize(1);
        assertThat(errorLogs.getFirst().getEventType()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("Should be strictly idempotent when processing same event multiple times")
    void testSequentialDuplicateProcessing() {
        String eventId = "evt-duplicate-" + UUID.randomUUID();
        LogEvent event = new LogEvent(eventId, Instant.now(), "order-service", "INFO", "ORDER_CREATED", "trace-dup", "Created", Map.of("attempt", 1));

        // Process 5 times sequentially
        Optional<ProcessedLogEvent> first = logProcessorService.processEvent(event);
        Optional<ProcessedLogEvent> second = logProcessorService.processEvent(event);
        Optional<ProcessedLogEvent> third = logProcessorService.processEvent(event);
        Optional<ProcessedLogEvent> fourth = logProcessorService.processEvent(event);
        Optional<ProcessedLogEvent> fifth = logProcessorService.processEvent(event);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(third).isPresent();
        assertThat(fourth).isPresent();
        assertThat(fifth).isPresent();

        Long storedId = first.get().getId();
        assertThat(second.get().getId()).isEqualTo(storedId);
        assertThat(third.get().getId()).isEqualTo(storedId);
        assertThat(fourth.get().getId()).isEqualTo(storedId);
        assertThat(fifth.get().getId()).isEqualTo(storedId);

        assertThat(logEventRepository.count()).isEqualTo(1);
        assertThat(logEventRepository.findByEventId(eventId)).isPresent();
    }

    @Test
    @DisplayName("Should be idempotent when processing identical event from raw JSON multiple times")
    void testRawJsonDuplicateProcessing() throws Exception {
        String eventId = "evt-raw-json-" + UUID.randomUUID();
        LogEvent event = new LogEvent(eventId, Instant.now(), "inventory-service", "INFO", "INVENTORY_RESERVED", "trace-json-dup", "Reserved", Map.of());
        String json = objectMapper.writeValueAsString(event);

        for (int i = 0; i < 4; i++) {
            Optional<ProcessedLogEvent> result = logProcessorService.processRawMessage(json);
            assertThat(result).isPresent();
            assertThat(result.get().getEventId()).isEqualTo(eventId);
        }

        assertThat(logEventRepository.count()).isEqualTo(1);
        assertThat(logEventRepository.findAll().getFirst().getEventId()).isEqualTo(eventId);
    }
}
