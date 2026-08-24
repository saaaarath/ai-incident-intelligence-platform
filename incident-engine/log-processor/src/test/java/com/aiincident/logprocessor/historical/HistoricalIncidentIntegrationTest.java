package com.aiincident.logprocessor.historical;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HistoricalIncidentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HistoricalIncidentRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Verification: At least 20 historical operational incidents seeded on startup")
    void testSeededIncidentsCount() {
        long count = repository.count();
        assertThat(count).isGreaterThanOrEqualTo(20);
    }

    @Test
    @DisplayName("Verification: Dataset covers all 8 required failure categories")
    void testAllEightCategoriesCovered() {
        List<HistoricalIncident> allIncidents = repository.findAll();
        EnumSet<HistoricalIncidentCategory> presentCategories = EnumSet.noneOf(HistoricalIncidentCategory.class);

        for (HistoricalIncident incident : allIncidents) {
            presentCategories.add(incident.getCategory());
        }

        assertThat(presentCategories).containsExactlyInAnyOrder(
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                HistoricalIncidentCategory.DEPLOYMENT_REGRESSION,
                HistoricalIncidentCategory.SERVICE_UNAVAILABLE,
                HistoricalIncidentCategory.NETWORK_LATENCY,
                HistoricalIncidentCategory.MEMORY_PRESSURE,
                HistoricalIncidentCategory.CACHE_FAILURE,
                HistoricalIncidentCategory.DEPENDENCY_TIMEOUT,
                HistoricalIncidentCategory.MESSAGE_PROCESSING_FAILURE
        );
    }

    @Test
    @DisplayName("Verification: Every historical incident has all required structured fields")
    void testMandatoryFieldsPopulated() {
        List<HistoricalIncident> allIncidents = repository.findAll();

        for (HistoricalIncident incident : allIncidents) {
            assertThat(incident.getIncidentId()).as("incidentId for " + incident.getTitle()).isNotBlank();
            assertThat(incident.getTitle()).as("title for " + incident.getIncidentId()).isNotBlank();
            assertThat(incident.getCategory()).as("category for " + incident.getIncidentId()).isNotNull();
            assertThat(incident.getSeverity()).as("severity for " + incident.getIncidentId()).isNotNull();

            // symptoms
            assertThat(incident.getSymptoms())
                    .as("symptoms for " + incident.getIncidentId())
                    .isNotEmpty()
                    .allMatch(s -> s != null && !s.isBlank());

            // timeline
            assertThat(incident.getTimeline())
                    .as("timeline for " + incident.getIncidentId())
                    .isNotEmpty()
                    .allMatch(t -> t != null && !t.isBlank());

            // root cause
            assertThat(incident.getRootCause())
                    .as("rootCause for " + incident.getIncidentId())
                    .isNotBlank();

            // resolution
            assertThat(incident.getResolution())
                    .as("resolution for " + incident.getIncidentId())
                    .isNotBlank();

            // affected services
            assertThat(incident.getAffectedServices())
                    .as("affectedServices for " + incident.getIncidentId())
                    .isNotEmpty();

            // prevention
            assertThat(incident.getPrevention())
                    .as("prevention for " + incident.getIncidentId())
                    .isNotBlank();

            assertThat(incident.getOccurredAt()).as("occurredAt for " + incident.getIncidentId()).isNotNull();
        }
    }

    @Test
    @DisplayName("REST API: GET /api/historical-incidents should return all seeded incidents")
    void testGetHistoricalIncidentsEndpoint() throws Exception {
        mockMvc.perform(get("/api/historical-incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(20))))
                .andExpect(jsonPath("$[0].incidentId").exists())
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(jsonPath("$[0].category").exists())
                .andExpect(jsonPath("$[0].rootCause").exists())
                .andExpect(jsonPath("$[0].resolution").exists())
                .andExpect(jsonPath("$[0].prevention").exists())
                .andExpect(jsonPath("$[0].symptoms").isArray())
                .andExpect(jsonPath("$[0].timeline").isArray())
                .andExpect(jsonPath("$[0].affectedServices").isArray());
    }

    @Test
    @DisplayName("REST API: GET /api/historical-incidents?category=... filters by category")
    void testFilterByCategory() throws Exception {
        mockMvc.perform(get("/api/historical-incidents")
                        .param("category", "DATABASE_CONNECTION_EXHAUSTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[0].category").value("DATABASE_CONNECTION_EXHAUSTION"));
    }

    @Test
    @DisplayName("REST API: GET /api/historical-incidents?service=... filters by affected service")
    void testFilterByService() throws Exception {
        mockMvc.perform(get("/api/historical-incidents")
                        .param("service", "payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].affectedServices").isArray());
    }

    @Test
    @DisplayName("REST API: GET /api/historical-incidents?query=... searches symptoms and root cause")
    void testSearchQuery() throws Exception {
        mockMvc.perform(get("/api/historical-incidents")
                        .param("query", "HikariCP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].title").value("Payment Service HikariCP Connection Pool Saturation During Flash Sale"));
    }

    @Test
    @DisplayName("REST API: GET /api/historical-incidents/{id} retrieves specific incident by incidentId code")
    void testGetByIncidentIdCode() throws Exception {
        mockMvc.perform(get("/api/historical-incidents/HIST-INC-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("HIST-INC-001"))
                .andExpect(jsonPath("$.category").value("DATABASE_CONNECTION_EXHAUSTION"))
                .andExpect(jsonPath("$.title").value("Payment Service HikariCP Connection Pool Saturation During Flash Sale"));
    }

    @Test
    @DisplayName("REST API: GET /api/historical-incidents/categories returns all 8 categories")
    void testGetCategoriesEndpoint() throws Exception {
        mockMvc.perform(get("/api/historical-incidents/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0]").exists());
    }

    @Test
    @DisplayName("REST API: POST /api/historical-incidents creates a custom historical record")
    void testCreateHistoricalIncident() throws Exception {
        HistoricalIncident custom = new HistoricalIncident(
                "HIST-INC-TEST-CUSTOM",
                "Custom Test Operational Incident",
                HistoricalIncidentCategory.NETWORK_LATENCY,
                AnomalySeverity.MEDIUM,
                List.of("Custom symptom A", "Custom symptom B"),
                List.of("10:00 UTC - Event start", "10:15 UTC - Resolved"),
                "Switch port misconfiguration",
                "Reconfigured switch port VLAN",
                Set.of("order-service", "inventory-service"),
                "Add automated switch config auditing",
                Instant.now(),
                15
        );

        mockMvc.perform(post("/api/historical-incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custom)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentId").value("HIST-INC-TEST-CUSTOM"))
                .andExpect(jsonPath("$.title").value("Custom Test Operational Incident"));
    }

    @Test
    @DisplayName("REST API: POST /api/historical-incidents/seed is idempotent")
    void testSeedIdempotency() throws Exception {
        mockMvc.perform(post("/api/historical-incidents/seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalCount").value(greaterThanOrEqualTo(20)));
    }
}
