package com.aiincident.logprocessor.historical.embedding;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentEvidence;
import com.aiincident.logprocessor.incident.IncidentEvidenceRepository;
import com.aiincident.logprocessor.incident.IncidentRepository;
import com.aiincident.logprocessor.incident.IncidentStatus;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentRetrievalServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentEvidenceRepository evidenceRepository;

    @Mock
    private SemanticRetrievalService semanticRetrievalService;

    private IncidentRetrievalService incidentRetrievalService;

    @BeforeEach
    void setUp() {
        incidentRetrievalService = new IncidentRetrievalService(
                incidentRepository,
                evidenceRepository,
                semanticRetrievalService
        );
    }

    private Incident createTestIncident() {
        Incident incident = new Incident(
                "Database Timeout Incident on payment-service",
                AnomalySeverity.CRITICAL,
                IncidentStatus.OPEN,
                "payment-service",
                Instant.now(),
                Instant.now(),
                "HikariPool-1 connections exhausted",
                "database.connection.timeout"
        );
        incident.setId(1L);
        incident.setRootService("payment-service");
        incident.setAffectedServices(Set.of("order-service", "payment-service"));
        return incident;
    }

    @Test
    @DisplayName("Summary synthesis: formats incident and evidence into descriptive text")
    void testSynthesizeIncidentSummary() {
        Incident incident = createTestIncident();
        IncidentEvidence ev1 = new IncidentEvidence(1L, "ev-1", Instant.now(), "payment-service", "DB_TIMEOUT",
                AnomalySeverity.CRITICAL, "Database connection pool timeout", "tr-1", "{}");
        IncidentEvidence ev2 = new IncidentEvidence(1L, "ev-2", Instant.now(), "payment-service", "CONNECTION_POOL_EXHAUSTED",
                AnomalySeverity.CRITICAL, "HikariPool exhausted", "tr-2", "{}");

        String summary = incidentRetrievalService.synthesizeIncidentSummary(incident, List.of(ev1, ev2));

        assertThat(summary).contains("Database Timeout Incident on payment-service");
        assertThat(summary).contains("Primary service: payment-service");
        assertThat(summary).contains("Affected services:");
        assertThat(summary).contains("DB_TIMEOUT");
        assertThat(summary).contains("CONNECTION_POOL_EXHAUSTED");
    }

    @Test
    @DisplayName("Incident similarity retrieval: queries semantic retrieval with synthesized summary")
    void testFindSimilarIncidents() {
        Incident incident = createTestIncident();
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(evidenceRepository.findByIncidentIdOrderByTimestampAsc(1L)).thenReturn(List.of());

        SemanticSearchResult mockResult = new SemanticSearchResult();
        mockResult.setDocumentId("INC:HIST-INC-001");
        mockResult.setSimilarityScore(0.92);

        when(semanticRetrievalService.findSimilarIncidents(contains("payment-service"), eq(3)))
                .thenReturn(List.of(mockResult));

        List<SemanticSearchResult> results = incidentRetrievalService.findSimilarIncidents("1", 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDocumentId()).isEqualTo("INC:HIST-INC-001");
        verify(semanticRetrievalService).findSimilarIncidents(contains("payment-service"), eq(3));
    }

    @Test
    @DisplayName("Runbook retrieval: queries semantic retrieval for relevant runbooks")
    void testFindRelevantRunbooks() {
        Incident incident = createTestIncident();
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(evidenceRepository.findByIncidentIdOrderByTimestampAsc(1L)).thenReturn(List.of());

        SemanticSearchResult mockRunbook = new SemanticSearchResult();
        mockRunbook.setDocumentId("RB:RB-DB-001");
        mockRunbook.setSimilarityScore(0.95);

        when(semanticRetrievalService.findRelevantRunbooks(contains("payment-service"), eq(2)))
                .thenReturn(List.of(mockRunbook));

        List<SemanticSearchResult> results = incidentRetrievalService.findRelevantRunbooks("1", 2);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDocumentId()).isEqualTo("RB:RB-DB-001");
    }

    @Test
    @DisplayName("Full context retrieval: aggregates similar incidents, runbooks, and postmortems")
    void testGetIncidentRetrievalContext() {
        Incident incident = createTestIncident();
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(evidenceRepository.findByIncidentIdOrderByTimestampAsc(1L)).thenReturn(List.of());

        Optional<IncidentRetrievalContext> context = incidentRetrievalService.getIncidentRetrievalContext("1", 3);

        assertThat(context).isPresent();
        assertThat(context.get().getTitle()).isEqualTo(incident.getTitle());
        assertThat(context.get().getPrimaryService()).isEqualTo("payment-service");
        assertThat(context.get().getSynthesizedSummary()).isNotBlank();
    }
}
