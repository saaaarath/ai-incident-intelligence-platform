package com.aiincident.logprocessor.historical;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunbookServiceTest {

    @Mock
    private RunbookRepository repository;

    @Mock
    private HistoricalIncidentSeeder seeder;

    private RunbookService service;
    private Runbook sampleRunbook;

    @BeforeEach
    void setUp() {
        service = new RunbookService(repository, seeder);
        sampleRunbook = new Runbook(
                "RB-DB-001",
                "Database Connection Pool Exhaustion Runbook",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                AnomalySeverity.CRITICAL,
                Set.of("payment-service", "postgres"),
                List.of("HikariPool connection timeout"),
                List.of("Verify psql access"),
                List.of("Terminate idle-in-transaction sessions"),
                List.of("Verify pool queue clears"),
                "Escalate to DBA on-call",
                "# Runbook content",
                Set.of("database", "postgres"),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should retrieve all runbooks")
    void testGetAllRunbooks() {
        when(repository.findAll()).thenReturn(List.of(sampleRunbook));

        List<Runbook> results = service.getAllRunbooks();
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getRunbookId()).isEqualTo("RB-DB-001");
    }

    @Test
    @DisplayName("Should retrieve runbook by runbookId")
    void testGetByRunbookId() {
        when(repository.findByRunbookId("RB-DB-001")).thenReturn(Optional.of(sampleRunbook));

        Optional<Runbook> result = service.getByRunbookId("RB-DB-001");
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).contains("Database");
    }

    @Test
    @DisplayName("Should search runbooks by query text")
    void testSearchRunbooks() {
        when(repository.searchRunbooks("HikariPool")).thenReturn(List.of(sampleRunbook));

        List<Runbook> results = service.searchRunbooks("HikariPool");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getRunbookId()).isEqualTo("RB-DB-001");
    }

    @Test
    @DisplayName("Should delegate seeding to HistoricalIncidentSeeder")
    void testSeedRunbooks() {
        when(seeder.seedCanonicalRunbooks()).thenReturn(8);

        int count = service.seedRunbooks();
        assertThat(count).isEqualTo(8);
        verify(seeder).seedCanonicalRunbooks();
    }
}
