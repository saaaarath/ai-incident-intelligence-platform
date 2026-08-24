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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalIncidentServiceTest {

    @Mock
    private HistoricalIncidentRepository repository;

    @Mock
    private HistoricalIncidentSeeder seeder;

    private HistoricalIncidentService service;

    private HistoricalIncident sampleIncident;

    @BeforeEach
    void setUp() {
        service = new HistoricalIncidentService(repository, seeder);
        sampleIncident = new HistoricalIncident(
                "HIST-INC-001",
                "Payment Service HikariCP Connection Pool Saturation During Flash Sale",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                AnomalySeverity.CRITICAL,
                List.of("HikariPool-1 connection timeout", "Payment error rate > 40%"),
                List.of("14:00 UTC - Flash sale traffic surge", "14:05 UTC - Pool exhausted"),
                "Missing index on payments table caused slow queries",
                "Created composite index and tuned HikariCP pool",
                Set.of("payment-service", "order-service", "postgres"),
                "Implement query plan CI checks and connection pool alerting",
                Instant.parse("2026-05-10T14:00:00Z"),
                15
        );
    }

    @Test
    @DisplayName("Should retrieve all historical incidents")
    void testGetAllIncidents() {
        when(repository.findAll()).thenReturn(List.of(sampleIncident));

        List<HistoricalIncident> results = service.getAllIncidents();
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getIncidentId()).isEqualTo("HIST-INC-001");
    }

    @Test
    @DisplayName("Should retrieve incident by incidentId")
    void testGetByIncidentId() {
        when(repository.findByIncidentId("HIST-INC-001")).thenReturn(Optional.of(sampleIncident));

        Optional<HistoricalIncident> result = service.getByIncidentId("HIST-INC-001");
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).contains("HikariCP");
    }

    @Test
    @DisplayName("Should filter incidents by category")
    void testGetByCategory() {
        when(repository.findByCategory(HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION))
                .thenReturn(List.of(sampleIncident));

        List<HistoricalIncident> results = service.getByCategory(HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getCategory()).isEqualTo(HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION);
    }

    @Test
    @DisplayName("Should search incidents across symptoms, root cause, and title")
    void testSearchIncidents() {
        when(repository.searchIncidents("HikariPool")).thenReturn(List.of(sampleIncident));

        List<HistoricalIncident> results = service.searchIncidents("HikariPool");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getIncidentId()).isEqualTo("HIST-INC-001");
    }

    @Test
    @DisplayName("Should delegate seeding to HistoricalIncidentSeeder")
    void testSeedDataset() {
        when(seeder.seedCanonicalDataset()).thenReturn(24);

        int count = service.seedDataset();
        assertThat(count).isEqualTo(24);
        verify(seeder).seedCanonicalDataset();
    }
}
