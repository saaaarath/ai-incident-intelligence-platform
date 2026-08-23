package com.aiincident.logprocessor.fingerprint;

import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.incident.EventTypeClassifier;
import com.aiincident.logprocessor.repository.LogEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErrorFingerprintServiceTest {

    @Mock
    private LogEventRepository logEventRepository;

    private ErrorFingerprintGenerator generator;
    private EventTypeClassifier eventTypeClassifier;
    private ErrorFingerprintService fingerprintService;

    @BeforeEach
    void setUp() {
        generator = new ErrorFingerprintGenerator();
        eventTypeClassifier = new EventTypeClassifier();
        fingerprintService = new ErrorFingerprintService(generator, logEventRepository, eventTypeClassifier);
    }

    @Test
    @DisplayName("Should group equivalent log events with different dynamic IDs under the same fingerprint")
    void testGroupEventsByFingerprint() {
        Instant t1 = Instant.parse("2026-08-23T14:00:00Z");
        Instant t2 = Instant.parse("2026-08-23T14:05:00Z");
        Instant t3 = Instant.parse("2026-08-23T14:10:00Z");

        ProcessedLogEvent e1 = new ProcessedLogEvent(
                "e1", t1, "payment-service", "ERROR", "DB_TIMEOUT", "tr-1",
                "Payment failed for order 11111111-1111-1111-1111-111111111111: timeout after 3000ms", null, t1
        );
        ProcessedLogEvent e2 = new ProcessedLogEvent(
                "e2", t2, "payment-service", "ERROR", "DB_TIMEOUT", "tr-2",
                "Payment failed for order 22222222-2222-2222-2222-222222222222: timeout after 4500ms", null, t2
        );
        ProcessedLogEvent e3 = new ProcessedLogEvent(
                "e3", t3, "payment-service", "ERROR", "POOL_EXHAUSTED", "tr-3",
                "Connection pool exhausted: active=100/100", null, t3
        );

        Map<String, List<ProcessedLogEvent>> grouped = fingerprintService.groupEventsByFingerprint(List.of(e1, e2, e3));

        assertThat(grouped).hasSize(2);

        ErrorFingerprint fpTimeout = generator.generateFingerprint("payment-service", "DB_TIMEOUT", e1.getMessage());
        assertThat(grouped.get(fpTimeout.fingerprintHash())).containsExactly(e1, e2);

        ErrorFingerprint fpPool = generator.generateFingerprint("payment-service", "POOL_EXHAUSTED", e3.getMessage());
        assertThat(grouped.get(fpPool.fingerprintHash())).containsExactly(e3);
    }

    @Test
    @DisplayName("Should calculate fingerprint summaries over time range")
    void testGetFingerprintSummaries() {
        Instant t1 = Instant.parse("2026-08-23T14:00:00Z");
        Instant t2 = Instant.parse("2026-08-23T14:15:00Z");

        ProcessedLogEvent e1 = new ProcessedLogEvent("e1", t1, "order-service", "ERROR", "SERVICE_UNAVAILABLE", "tr-1", "Downstream payment-service unavailable for order 12345", null, t1);
        ProcessedLogEvent e2 = new ProcessedLogEvent("e2", t2, "order-service", "ERROR", "SERVICE_UNAVAILABLE", "tr-2", "Downstream payment-service unavailable for order 67890", null, t2);

        when(logEventRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of(e1, e2));

        List<ErrorFingerprintService.FingerprintSummary> summaries = fingerprintService.getFingerprintSummaries(t1.minusSeconds(60), t2.plusSeconds(60), null);

        assertThat(summaries).hasSize(1);
        ErrorFingerprintService.FingerprintSummary summary = summaries.getFirst();
        assertThat(summary.service()).isEqualTo("order-service");
        assertThat(summary.eventType()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.sampleEventIds()).containsExactly("e1", "e2");
    }
}
