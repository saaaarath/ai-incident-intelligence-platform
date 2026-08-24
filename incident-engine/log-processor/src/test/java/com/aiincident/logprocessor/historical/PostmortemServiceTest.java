package com.aiincident.logprocessor.historical;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
class PostmortemServiceTest {

    @Mock
    private PostmortemRepository repository;

    @Mock
    private HistoricalIncidentSeeder seeder;

    private PostmortemService service;
    private Postmortem samplePostmortem;

    @BeforeEach
    void setUp() {
        service = new PostmortemService(repository, seeder);
        samplePostmortem = new Postmortem(
                "PM-HIST-INC-001",
                "HIST-INC-001",
                "Payment Service HikariCP Connection Pool Saturation Postmortem",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                AnomalySeverity.CRITICAL,
                "Lead SRE",
                "Executive summary of the pool saturation",
                "1450 orders failed over 14 minutes",
                "Missing index on payments table",
                "Detected by error rate anomaly",
                List.of("Add index", "Increase pool size"),
                List.of("Load test query volume", "Tune alerts"),
                "# Postmortem content",
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should retrieve all postmortems")
    void testGetAllPostmortems() {
        when(repository.findAll()).thenReturn(List.of(samplePostmortem));

        List<Postmortem> results = service.getAllPostmortems();
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getPostmortemId()).isEqualTo("PM-HIST-INC-001");
    }

    @Test
    @DisplayName("Should retrieve postmortem by incidentId")
    void testGetByIncidentId() {
        when(repository.findByIncidentId("HIST-INC-001")).thenReturn(Optional.of(samplePostmortem));

        Optional<Postmortem> result = service.getByIncidentId("HIST-INC-001");
        assertThat(result).isPresent();
        assertThat(result.get().getIncidentId()).isEqualTo("HIST-INC-001");
    }

    @Test
    @DisplayName("Should search postmortems by query text")
    void testSearchPostmortems() {
        when(repository.searchPostmortems("Missing index")).thenReturn(List.of(samplePostmortem));

        List<Postmortem> results = service.searchPostmortems("Missing index");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getPostmortemId()).isEqualTo("PM-HIST-INC-001");
    }

    @Test
    @DisplayName("Should delegate seeding to HistoricalIncidentSeeder")
    void testSeedPostmortems() {
        when(seeder.seedCanonicalPostmortems()).thenReturn(9);

        int count = service.seedPostmortems();
        assertThat(count).isEqualTo(9);
        verify(seeder).seedCanonicalPostmortems();
    }
}
