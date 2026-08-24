package com.aiincident.logprocessor.historical;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class OperationalKnowledgeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RunbookRepository runbookRepository;

    @Autowired
    private PostmortemRepository postmortemRepository;

    @Autowired
    private HistoricalIncidentRepository incidentRepository;

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    @Test
    @DisplayName("Verification: Runbooks for all 8 failure domains are seeded in PostgreSQL")
    void testRunbooksSeededForAllCategories() {
        List<Runbook> allRunbooks = runbookRepository.findAll();
        assertThat(allRunbooks).hasSizeGreaterThanOrEqualTo(8);

        EnumSet<HistoricalIncidentCategory> covered = EnumSet.noneOf(HistoricalIncidentCategory.class);
        for (Runbook rb : allRunbooks) {
            covered.add(rb.getCategory());
            assertThat(rb.getRunbookId()).isNotBlank();
            assertThat(rb.getTitle()).isNotBlank();
            assertThat(rb.getMitigationSteps()).isNotEmpty();
            assertThat(rb.getVerificationSteps()).isNotEmpty();
            assertThat(rb.getEscalationPath()).isNotBlank();
            assertThat(rb.getContent()).isNotBlank();
        }

        assertThat(covered).contains(
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
    @DisplayName("Verification: Postmortems are seeded and linked to historical incidents")
    void testPostmortemsSeededAndLinked() {
        List<Postmortem> allPostmortems = postmortemRepository.findAll();
        assertThat(allPostmortems).isNotEmpty();

        for (Postmortem pm : allPostmortems) {
            assertThat(pm.getPostmortemId()).isNotBlank();
            assertThat(pm.getIncidentId()).isNotBlank();
            assertThat(pm.getTitle()).isNotBlank();
            assertThat(pm.getExecutiveSummary()).isNotBlank();
            assertThat(pm.getRootCauseAnalysis()).isNotBlank();
            assertThat(pm.getActionItems()).isNotEmpty();
            assertThat(pm.getLessonsLearned()).isNotEmpty();
            assertThat(pm.getContent()).isNotBlank();

            // Verify linked incident exists in dataset
            assertThat(incidentRepository.findByIncidentId(pm.getIncidentId())).isPresent();
        }
    }

    @Test
    @DisplayName("Verification: Unified KnowledgeDocument model indexes incidents, postmortems, and runbooks")
    void testKnowledgeDocumentServiceAggregatesAllTypes() {
        List<KnowledgeDocument> allDocs = knowledgeDocumentService.getAllDocuments();
        assertThat(allDocs).isNotEmpty();

        boolean hasIncident = allDocs.stream().anyMatch(d -> d.getDocumentType() == KnowledgeDocumentType.HISTORICAL_INCIDENT);
        boolean hasPostmortem = allDocs.stream().anyMatch(d -> d.getDocumentType() == KnowledgeDocumentType.POSTMORTEM);
        boolean hasRunbook = allDocs.stream().anyMatch(d -> d.getDocumentType() == KnowledgeDocumentType.RUNBOOK);

        assertThat(hasIncident).isTrue();
        assertThat(hasPostmortem).isTrue();
        assertThat(hasRunbook).isTrue();

        for (KnowledgeDocument doc : allDocs) {
            assertThat(doc.getDocumentId()).isNotBlank();
            assertThat(doc.getContent()).as("content for " + doc.getDocumentId()).isNotBlank();
            assertThat(doc.getCategory()).as("category for " + doc.getDocumentId()).isNotNull();
        }
    }

    @Test
    @DisplayName("REST API: GET /api/runbooks returns seeded runbooks")
    void testGetRunbooksEndpoint() throws Exception {
        mockMvc.perform(get("/api/runbooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(8))))
                .andExpect(jsonPath("$[0].runbookId").exists())
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(jsonPath("$[0].mitigationSteps").isArray())
                .andExpect(jsonPath("$[0].verificationSteps").isArray());
    }

    @Test
    @DisplayName("REST API: GET /api/runbooks/{id} retrieves specific runbook")
    void testGetRunbookByIdEndpoint() throws Exception {
        mockMvc.perform(get("/api/runbooks/RB-DB-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runbookId").value("RB-DB-001"))
                .andExpect(jsonPath("$.category").value("DATABASE_CONNECTION_EXHAUSTION"))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    @DisplayName("REST API: GET /api/postmortems returns postmortem records")
    void testGetPostmortemsEndpoint() throws Exception {
        mockMvc.perform(get("/api/postmortems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].postmortemId").exists())
                .andExpect(jsonPath("$[0].incidentId").exists())
                .andExpect(jsonPath("$[0].actionItems").isArray())
                .andExpect(jsonPath("$[0].lessonsLearned").isArray());
    }

    @Test
    @DisplayName("REST API: GET /api/postmortems?incidentId=HIST-INC-001 retrieves linked postmortem")
    void testGetPostmortemByIncidentIdParam() throws Exception {
        mockMvc.perform(get("/api/postmortems")
                        .param("incidentId", "HIST-INC-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].incidentId").value("HIST-INC-001"))
                .andExpect(jsonPath("$[0].postmortemId").value("PM-HIST-INC-001"));
    }

    @Test
    @DisplayName("REST API: GET /api/knowledge searches across incidents, postmortems, and runbooks")
    void testKnowledgeSearchEndpoint() throws Exception {
        mockMvc.perform(get("/api/knowledge")
                        .param("query", "HikariCP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].documentId").exists())
                .andExpect(jsonPath("$[0].content").exists());
    }

    @Test
    @DisplayName("REST API: GET /api/knowledge/{documentId} retrieves structured document")
    void testKnowledgeGetDocumentById() throws Exception {
        mockMvc.perform(get("/api/knowledge/RB:RB-DB-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("RB:RB-DB-001"))
                .andExpect(jsonPath("$.documentType").value("RUNBOOK"))
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("REST API: POST /api/knowledge/seed is idempotent and returns counts")
    void testKnowledgeSeedEndpoint() throws Exception {
        mockMvc.perform(post("/api/knowledge/seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalKnowledgeDocuments").value(greaterThanOrEqualTo(30)));
    }
}
