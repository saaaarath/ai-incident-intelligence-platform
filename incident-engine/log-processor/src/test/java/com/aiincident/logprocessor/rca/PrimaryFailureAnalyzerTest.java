package com.aiincident.logprocessor.rca;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.aiincident.logprocessor.dependency.ServiceDependency;
import com.aiincident.logprocessor.dependency.ServiceDependencyService;
import com.aiincident.logprocessor.dependency.ServiceDependencyType;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentEvidence;
import com.aiincident.logprocessor.incident.IncidentStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrimaryFailureAnalyzerTest {

    @Mock
    private ServiceDependencyService dependencyService;

    private PrimaryFailureAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        // Mock topology:
        // order-service -> payment-service
        // payment-service -> postgres
        when(dependencyService.getDownstream("order-service"))
                .thenReturn(List.of(new ServiceDependency("order-service", "payment-service", ServiceDependencyType.HTTP_REST, "HIGH", "")));
        when(dependencyService.getUpstream("order-service"))
                .thenReturn(List.of());

        when(dependencyService.getDownstream("payment-service"))
                .thenReturn(List.of(new ServiceDependency("payment-service", "postgres", ServiceDependencyType.DATABASE, "HIGH", "")));
        when(dependencyService.getUpstream("payment-service"))
                .thenReturn(List.of(new ServiceDependency("order-service", "payment-service", ServiceDependencyType.HTTP_REST, "HIGH", "")));

        when(dependencyService.getDownstream("postgres"))
                .thenReturn(List.of());
        when(dependencyService.getUpstream("postgres"))
                .thenReturn(List.of(new ServiceDependency("payment-service", "postgres", ServiceDependencyType.DATABASE, "HIGH", "")));

        analyzer = new PrimaryFailureAnalyzer(dependencyService, null);
    }

    @Test
    @DisplayName("Should identify PostgreSQL as primary failure and Payment + Order as downstream symptoms")
    void testCascadingFailureAnalysisPostgresToPaymentToOrder() {
        Instant t0 = Instant.parse("2026-08-23T14:00:00Z");
        Instant t1 = Instant.parse("2026-08-23T14:00:02Z");
        Instant t2 = Instant.parse("2026-08-23T14:00:05Z");

        IncidentEvidence evPostgres = new IncidentEvidence(
                1L, "ev-pg", t0, "postgres", "DB_FAILURE",
                AnomalySeverity.CRITICAL, "PostgreSQL disk full / connection timeout", "tr-1", "{}"
        );
        IncidentEvidence evPayment = new IncidentEvidence(
                1L, "ev-pay", t1, "payment-service", "DB_TIMEOUT",
                AnomalySeverity.HIGH, "Payment failed: database query timeout after 3000ms", "tr-1", "{}"
        );
        IncidentEvidence evOrder = new IncidentEvidence(
                1L, "ev-ord", t2, "order-service", "SERVICE_UNAVAILABLE",
                AnomalySeverity.HIGH, "Order failed: downstream payment-service returned 503", "tr-1", "{}"
        );

        Incident incident = new Incident(
                "Cascading Database Outage",
                AnomalySeverity.CRITICAL,
                IncidentStatus.OPEN,
                "order-service",
                t0,
                t2,
                "Cascading failure from database",
                "DB_TIMEOUT"
        );
        incident.setEvidence(List.of(evPostgres, evPayment, evOrder));

        PrimaryFailureAnalysis analysis = analyzer.analyzeIncident(incident);

        assertThat(analysis).isNotNull();
        PrimaryFailureCandidate primary = analysis.primaryCandidate();
        assertThat(primary.service()).isEqualTo("postgres");
        assertThat(primary.isPrimary()).isTrue();
        assertThat(primary.isSymptom()).isFalse();
        assertThat(primary.score()).isGreaterThan(70.0);
        assertThat(primary.confidence()).isEqualTo("HIGH");

        // Verify downstream symptoms
        List<String> symptomNames = analysis.symptoms().stream().map(PrimaryFailureCandidate::service).toList();
        assertThat(symptomNames).containsExactlyInAnyOrder("payment-service", "order-service");

        // Verify primary records symptoms list
        assertThat(primary.symptomServices()).containsExactlyInAnyOrder("payment-service", "order-service");

        // Verify explanation reasons exist
        assertThat(primary.reasons()).isNotEmpty();
        assertThat(analysis.summary()).contains("postgres");
    }

    @Test
    @DisplayName("Should score payment-service as primary when payment database times out without postgres events")
    void testDirectPaymentServiceFailure() {
        Instant t0 = Instant.parse("2026-08-23T14:00:00Z");
        Instant t1 = Instant.parse("2026-08-23T14:00:03Z");

        IncidentEvidence evPayment = new IncidentEvidence(
                2L, "ev-pay", t0, "payment-service", "POOL_EXHAUSTED",
                AnomalySeverity.CRITICAL, "Payment connection pool exhausted", "tr-2", "{}"
        );
        IncidentEvidence evOrder = new IncidentEvidence(
                2L, "ev-ord", t1, "order-service", "SERVICE_UNAVAILABLE",
                AnomalySeverity.HIGH, "Order processing failed: payment timeout", "tr-2", "{}"
        );

        PrimaryFailureAnalysis analysis = analyzer.analyzeEvidence(2L, List.of(evPayment, evOrder), "payment-service");

        assertThat(analysis.primaryCandidate().service()).isEqualTo("payment-service");
        assertThat(analysis.primaryCandidate().isPrimary()).isTrue();
        assertThat(analysis.symptoms()).extracting(PrimaryFailureCandidate::service).containsExactly("order-service");
    }
}
