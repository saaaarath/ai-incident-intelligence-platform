package com.aiincident.logprocessor.rca;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for persisting, mapping, and retrieving RCA reports associated with Incidents.
 */
@Service
public class RcaPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(RcaPersistenceService.class);

    private final RcaReportRepository rcaReportRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public RcaPersistenceService(
            RcaReportRepository rcaReportRepository,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this.rcaReportRepository = rcaReportRepository;
        this.objectMapper = (objectMapper != null)
                ? objectMapper.copy().registerModule(new JavaTimeModule()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                : new ObjectMapper().registerModule(new JavaTimeModule()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Persist an RCA report to the database.
     */
    @Transactional
    public RcaReport saveReport(RcaReport report, Long numericIncidentId, String incidentId) {
        if (report == null) {
            throw new IllegalArgumentException("RcaReport must not be null");
        }

        String resolvedIncidentId = (incidentId != null && !incidentId.isBlank())
                ? incidentId
                : (report.metadata() != null && report.metadata().incidentIdentifier() != null ? report.metadata().incidentIdentifier() : "unknown");

        Long resolvedNumericId = numericIncidentId;
        if (resolvedNumericId == null && resolvedIncidentId.matches("\\d+")) {
            try {
                resolvedNumericId = Long.parseLong(resolvedIncidentId);
            } catch (NumberFormatException ignored) {}
        }

        RcaReportEntity entity = toEntity(report, resolvedNumericId, resolvedIncidentId);
        RcaReportEntity saved = rcaReportRepository.save(entity);
        log.info("Persisted RCA report [id={}, incidentId='{}', numericId={}]", saved.getId(), resolvedIncidentId, resolvedNumericId);

        return toDto(saved);
    }

    /**
     * Retrieve the latest persisted RCA report for an incident identifier (string or numeric).
     */
    @Transactional(readOnly = true)
    public Optional<RcaReport> getLatestReport(String incidentIdentifier) {
        if (incidentIdentifier == null || incidentIdentifier.isBlank()) {
            return Optional.empty();
        }

        // Try lookup by string incidentId
        Optional<RcaReportEntity> entityOpt = rcaReportRepository.findFirstByIncidentIdOrderByCreatedAtDesc(incidentIdentifier.trim());

        // If not found and numeric, try numeric lookup
        if (entityOpt.isEmpty() && incidentIdentifier.trim().matches("\\d+")) {
            try {
                long numId = Long.parseLong(incidentIdentifier.trim());
                entityOpt = rcaReportRepository.findFirstByNumericIncidentIdOrderByCreatedAtDesc(numId);
            } catch (NumberFormatException ignored) {}
        }

        return entityOpt.map(this::toDto);
    }

    /**
     * Retrieve the latest persisted RCA report for a numeric incident ID.
     */
    @Transactional(readOnly = true)
    public Optional<RcaReport> getLatestReport(Long numericIncidentId) {
        if (numericIncidentId == null) {
            return Optional.empty();
        }
        return rcaReportRepository.findFirstByNumericIncidentIdOrderByCreatedAtDesc(numericIncidentId)
                .or(() -> rcaReportRepository.findFirstByIncidentIdOrderByCreatedAtDesc(String.valueOf(numericIncidentId)))
                .map(this::toDto);
    }

    /**
     * Check if a persisted RCA report already exists for the incident.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveAnalysis(String incidentIdentifier) {
        if (incidentIdentifier == null || incidentIdentifier.isBlank()) {
            return false;
        }
        if (rcaReportRepository.existsByIncidentId(incidentIdentifier.trim())) {
            return true;
        }
        if (incidentIdentifier.trim().matches("\\d+")) {
            try {
                long numId = Long.parseLong(incidentIdentifier.trim());
                return rcaReportRepository.existsByNumericIncidentId(numId);
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    public RcaReportEntity toEntity(RcaReport report, Long numericIncidentId, String incidentId) {
        String rootStmt = report.rootCause() != null ? report.rootCause().statement() : "Unknown";
        String rootCat = report.rootCause() != null ? report.rootCause().category() : "UNKNOWN";
        String rootSvc = report.rootCause() != null ? report.rootCause().rootService() : "unknown";
        String rootInf = report.rootCause() != null ? report.rootCause().inferenceDetails() : "";
        boolean isObserved = report.rootCause() != null && report.rootCause().isDirectlyObserved();

        String confLevel = report.confidence() != null ? report.confidence().level() : "LOW";
        double confScore = report.confidence() != null ? report.confidence().score() : 0.0;
        String confRationale = report.confidence() != null ? report.confidence().rationale() : "";

        String provider = report.metadata() != null ? report.metadata().provider() : "unknown";
        String model = report.metadata() != null ? report.metadata().model() : "unknown";
        long latency = report.metadata() != null ? report.metadata().executionLatencyMs() : 0;
        Instant createdAt = report.metadata() != null && report.metadata().analyzedAt() != null
                ? report.metadata().analyzedAt()
                : Instant.now();

        String valStatus = report.validation() != null && report.validation().status() != null
                ? report.validation().status().name()
                : "UNVALIDATED";

        return new RcaReportEntity(
                incidentId,
                numericIncidentId,
                rootStmt,
                rootCat,
                rootSvc,
                rootInf,
                isObserved,
                confLevel,
                confScore,
                confRationale,
                toJson(report.evidence()),
                toJson(report.alternativeHypotheses()),
                toJson(report.affectedServices()),
                toJson(report.recommendedInvestigation()),
                toJson(report.historicalReferences()),
                toJson(report.uncertaintyNotes()),
                valStatus,
                toJson(report.validation()),
                toJson(report),
                provider,
                model,
                latency,
                createdAt
        );
    }

    public RcaReport toDto(RcaReportEntity entity) {
        if (entity == null) {
            return null;
        }

        // If full report JSON is stored and parses cleanly, return it directly
        if (entity.getFullReportJson() != null && !entity.getFullReportJson().isBlank()) {
            try {
                return objectMapper.readValue(entity.getFullReportJson(), RcaReport.class);
            } catch (Exception e) {
                log.debug("Failed to deserialize fullReportJson from entity #{}, falling back to column assembly: {}", entity.getId(), e.getMessage());
            }
        }

        // Fallback assembly from individual structured columns
        RcaReport.RootCause rootCause = new RcaReport.RootCause(
                entity.getRootCauseStatement(),
                entity.getRootCauseCategory(),
                entity.getRootService(),
                entity.getRootCauseInferenceDetails(),
                entity.isDirectlyObserved()
        );

        RcaReport.Confidence confidence = new RcaReport.Confidence(
                entity.getConfidenceLevel(),
                entity.getConfidenceScore(),
                entity.getConfidenceRationale()
        );

        List<RcaReport.EvidenceItem> evidence = fromJson(entity.getEvidenceJson(), new TypeReference<>() {});
        List<RcaReport.AlternativeHypothesis> hypotheses = fromJson(entity.getAlternativeHypothesesJson(), new TypeReference<>() {});
        RcaReport.AffectedServices affectedServices = fromJson(entity.getAffectedServicesJson(), new TypeReference<>() {});
        List<RcaReport.RecommendedInvestigation> recommendations = fromJson(entity.getRecommendationsJson(), new TypeReference<>() {});
        List<RcaReport.HistoricalReference> historicalReferences = fromJson(entity.getHistoricalReferencesJson(), new TypeReference<>() {});
        List<String> uncertaintyNotes = fromJson(entity.getUncertaintyNotesJson(), new TypeReference<>() {});
        RcaValidationResult validation = fromJson(entity.getValidationJson(), new TypeReference<>() {});

        RcaReport.RcaReportMetadata metadata = new RcaReport.RcaReportMetadata(
                entity.getCreatedAt(),
                entity.getProvider(),
                entity.getModel(),
                entity.getExecutionLatencyMs(),
                entity.getIncidentId()
        );

        return new RcaReport(
                rootCause,
                confidence,
                evidence != null ? evidence : List.of(),
                hypotheses != null ? hypotheses : List.of(),
                affectedServices != null ? affectedServices : new RcaReport.AffectedServices(entity.getRootService(), List.of(), Map.of()),
                recommendations != null ? recommendations : List.of(),
                historicalReferences != null ? historicalReferences : List.of(),
                uncertaintyNotes != null ? uncertaintyNotes : List.of(),
                metadata,
                validation
        );
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return null;
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON string: {}", e.getMessage());
            return null;
        }
    }
}
