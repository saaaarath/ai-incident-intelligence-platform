package com.aiincident.logprocessor.rca;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RcaPersistenceServiceTest {

    @Mock
    private RcaReportRepository rcaReportRepository;

    private RcaPersistenceService persistenceService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        persistenceService = new RcaPersistenceService(rcaReportRepository, objectMapper);
    }

    @Test
    @DisplayName("saveReport converts RcaReport to RcaReportEntity and persists it")
    void testSaveReport() {
        Instant now = Instant.now();
        RcaReport report = new RcaReport(
                new RcaReport.RootCause("Hikari connection pool timeout", "DATABASE_CONNECTION_EXHAUSTION", "payment-service", "Details", true),
                new RcaReport.Confidence("HIGH", 0.95, "Observed direct log"),
                List.of(new RcaReport.EvidenceItem("LOG", "payment-service", "HikariPool timeout", "ev-1", true, now)),
                List.of(new RcaReport.AlternativeHypothesis("Network drop", "LOW", "DB error logged", "None")),
                new RcaReport.AffectedServices("payment-service", List.of("order-service"), Map.of("payment-service", "20% error rate")),
                List.of(new RcaReport.RecommendedInvestigation("Scale pool size", "HIGH", "Runbook recommendation", "RB-001")),
                List.of(new RcaReport.HistoricalReference("HIST-INC-001", "Postgres Connection Exhaustion", "Same pool timeout", "Scaled pool")),
                List.of("No lock contention telemetry"),
                new RcaReport.RcaReportMetadata(now, "mock", "mock-sre-engine", 150, "INC-101"),
                RcaValidationResult.valid()
        );

        RcaReportEntity entity = persistenceService.toEntity(report, 101L, "INC-101");
        entity.setId(1L);

        when(rcaReportRepository.save(any(RcaReportEntity.class))).thenReturn(entity);

        RcaReport saved = persistenceService.saveReport(report, 101L, "INC-101");

        assertThat(saved).isNotNull();
        assertThat(saved.rootCause().statement()).isEqualTo("Hikari connection pool timeout");
        assertThat(saved.rootCause().rootService()).isEqualTo("payment-service");
        assertThat(saved.confidence().score()).isEqualTo(0.95);
        assertThat(saved.metadata().incidentIdentifier()).isEqualTo("INC-101");
        verify(rcaReportRepository).save(any(RcaReportEntity.class));
    }

    @Test
    @DisplayName("getLatestReport returns persisted analysis when available")
    void testGetLatestReport() {
        Instant now = Instant.now();
        RcaReport report = new RcaReport(
                new RcaReport.RootCause("Hikari connection pool timeout", "DATABASE_CONNECTION_EXHAUSTION", "payment-service", "Details", true),
                new RcaReport.Confidence("HIGH", 0.95, "Observed direct log"),
                List.of(),
                List.of(),
                new RcaReport.AffectedServices("payment-service", List.of(), Map.of()),
                List.of(),
                List.of(),
                List.of(),
                new RcaReport.RcaReportMetadata(now, "mock", "mock-sre-engine", 150, "INC-101"),
                RcaValidationResult.valid()
        );

        RcaReportEntity entity = persistenceService.toEntity(report, 101L, "INC-101");
        entity.setId(1L);

        when(rcaReportRepository.findFirstByIncidentIdOrderByCreatedAtDesc("INC-101")).thenReturn(Optional.of(entity));

        Optional<RcaReport> retrieved = persistenceService.getLatestReport("INC-101");

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().rootCause().rootService()).isEqualTo("payment-service");
    }

    @Test
    @DisplayName("hasActiveAnalysis checks existence in repository")
    void testHasActiveAnalysis() {
        when(rcaReportRepository.existsByIncidentId("INC-101")).thenReturn(true);
        when(rcaReportRepository.existsByIncidentId("INC-999")).thenReturn(false);

        assertThat(persistenceService.hasActiveAnalysis("INC-101")).isTrue();
        assertThat(persistenceService.hasActiveAnalysis("INC-999")).isFalse();
    }
}
