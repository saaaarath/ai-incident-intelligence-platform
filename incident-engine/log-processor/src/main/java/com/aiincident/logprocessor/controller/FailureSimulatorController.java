package com.aiincident.logprocessor.controller;

import com.aiincident.logging.deployment.DeploymentEvent;
import com.aiincident.logprocessor.entity.ProcessedDeploymentEvent;
import com.aiincident.logprocessor.service.DeploymentProcessorService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Development & Demo Failure Injection Controller.
 * Provides controls for simulating realistic microservice failures:
 * 1. DB Connection Exhaustion
 * 2. Payment Latency
 * 3. Inventory Failure
 * 4. Error Spike
 * 5. Deployment Regression
 */
import java.util.Set;
import java.util.HashSet;
import com.aiincident.logprocessor.incident.Incident;
import com.aiincident.logprocessor.incident.IncidentRepository;
import com.aiincident.logprocessor.incident.IncidentStatus;
import com.aiincident.logprocessor.anomaly.AnomalySeverity;

@RestController
@RequestMapping("/api/failures")
public class FailureSimulatorController {

    private static final Logger log = LoggerFactory.getLogger(FailureSimulatorController.class);

    private final DeploymentProcessorService deploymentService;
    private final IncidentRepository incidentRepository;
    private final RestClient restClient;

    @Value("${failure.injection.demo-mode:true}")
    private boolean demoModeEnabled;

    @Value("${services.order-url:http://localhost:8081}")
    private String orderServiceUrl;

    @Value("${services.payment-url:http://localhost:8082}")
    private String paymentServiceUrl;

    @Value("${services.inventory-url:http://localhost:8083}")
    private String inventoryServiceUrl;

    // In-memory active failure scenario tracking
    private final Map<String, ActiveFailureScenario> activeScenarios = new ConcurrentHashMap<>();

    @Autowired
    public FailureSimulatorController(
            DeploymentProcessorService deploymentService,
            IncidentRepository incidentRepository) {
        this.deploymentService = deploymentService;
        this.incidentRepository = incidentRepository;
        this.restClient = RestClient.builder().build();
    }

    public record FailureInjectionRequest(
            String scenario,
            String service,
            Long latencyMs,
            String description,
            String version,
            Map<String, Object> parameters
    ) {}

    public record ActiveFailureScenario(
            String id,
            String scenario,
            String service,
            String failureType,
            Long latencyMs,
            String description,
            Instant injectedAt,
            String status
    ) {}

    public record SimulatorStatusResponse(
            boolean demoMode,
            int activeCount,
            List<ActiveFailureScenario> activeScenarios,
            List<String> supportedScenarios
    ) {}

    @GetMapping("/status")
    public ResponseEntity<SimulatorStatusResponse> getStatus() {
        List<ActiveFailureScenario> list = new ArrayList<>(activeScenarios.values());
        List<String> supported = List.of(
                "DB_CONNECTION_EXHAUSTION",
                "PAYMENT_LATENCY",
                "INVENTORY_FAILURE",
                "ERROR_SPIKE",
                "DEPLOYMENT_REGRESSION"
        );

        return ResponseEntity.ok(new SimulatorStatusResponse(
                demoModeEnabled,
                list.size(),
                list,
                supported
        ));
    }

    @PostMapping("/inject")
    public ResponseEntity<?> injectFailure(
            @RequestHeader(name = "X-Demo-Mode", required = false) String demoHeader,
            @RequestBody FailureInjectionRequest request) {

        if (!demoModeEnabled || "false".equalsIgnoreCase(demoHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Failure injection is protected and requires Development/Demo mode to be enabled."));
        }

        if (request == null || request.scenario() == null || request.scenario().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing failure scenario identifier."));
        }

        String scenarioId = "FAIL-" + UUID.randomUUID().toString().substring(0, 8);
        String scenario = request.scenario().toUpperCase().trim();
        String targetService = (request.service() != null && !request.service().isBlank()) ? request.service().trim() : "payment-service";

        log.info("Initiating failure injection scenario='{}' on target='{}' [scenarioId={}]", scenario, targetService, scenarioId);

        Long createdIncidentId = null;

        try {
            switch (scenario) {
                case "DB_CONNECTION_EXHAUSTION" -> {
                    try {
                        callServiceFailureApi(paymentServiceUrl, "CONNECTION_POOL_EXHAUSTED", null);
                    } catch (Exception ignored) {}

                    Incident inc = createSimulatedIncident(
                            "High Connection Pool Exhaustion in payment-service",
                            AnomalySeverity.CRITICAL,
                            "payment-service",
                            "payment-service",
                            Set.of("payment-service", "order-service", "postgres"),
                            "hikaricp_pending_threads",
                            "HikariCP database connection pool capacity at 98% saturation. Latency spikes and connection acquisition timeouts observed across checkout transactions."
                    );
                    createdIncidentId = inc.getId();

                    activeScenarios.put(scenarioId, new ActiveFailureScenario(
                            scenarioId,
                            "DB_CONNECTION_EXHAUSTION",
                            "payment-service",
                            "CONNECTION_POOL_EXHAUSTED",
                            null,
                            "HikariCP connection pool capacity exhausted (98% saturation)",
                            Instant.now(),
                            "ACTIVE"
                    ));
                }

                case "PAYMENT_LATENCY" -> {
                    long latency = (request.latencyMs() != null && request.latencyMs() > 0) ? request.latencyMs() : 3000L;
                    try {
                        callServiceFailureApi(paymentServiceUrl, "LATENCY", latency);
                    } catch (Exception ignored) {}

                    Incident inc = createSimulatedIncident(
                            "Payment Gateway Response Latency Surge (3,000ms)",
                            AnomalySeverity.HIGH,
                            "payment-service",
                            "payment-service",
                            Set.of("payment-service", "order-service"),
                            "http_request_duration_ms",
                            String.format("Payment authorization latency surged to %dms causing cascading checkout timeouts.", latency)
                    );
                    createdIncidentId = inc.getId();

                    activeScenarios.put(scenarioId, new ActiveFailureScenario(
                            scenarioId,
                            "PAYMENT_LATENCY",
                            "payment-service",
                            "LATENCY",
                            latency,
                            String.format("Injected %dms network/processing latency on payment-service", latency),
                            Instant.now(),
                            "ACTIVE"
                    ));
                }

                case "INVENTORY_FAILURE" -> {
                    try {
                        callServiceFailureApi(inventoryServiceUrl, "SERVICE_UNAVAILABLE", null);
                    } catch (Exception ignored) {}

                    Incident inc = createSimulatedIncident(
                            "Inventory Reservation 503 Service Unavailable Outage",
                            AnomalySeverity.HIGH,
                            "inventory-service",
                            "inventory-service",
                            Set.of("inventory-service", "order-service"),
                            "cache_hit_ratio",
                            "Inventory stock reservation endpoint returning continuous HTTP 503 Service Unavailable errors."
                    );
                    createdIncidentId = inc.getId();

                    activeScenarios.put(scenarioId, new ActiveFailureScenario(
                            scenarioId,
                            "INVENTORY_FAILURE",
                            "inventory-service",
                            "SERVICE_UNAVAILABLE",
                            null,
                            "Inventory reservation returning 503 Service Unavailable",
                            Instant.now(),
                            "ACTIVE"
                    ));
                }

                case "ERROR_SPIKE" -> {
                    try {
                        callServiceFailureApi(targetService.contains("order") ? orderServiceUrl : paymentServiceUrl, "ERROR_SPIKE", null);
                    } catch (Exception ignored) {}

                    Incident inc = createSimulatedIncident(
                            "Elevated HTTP 500 Error Burst on Checkout API",
                            AnomalySeverity.CRITICAL,
                            "order-service",
                            "order-service",
                            Set.of("order-service", "payment-service"),
                            "http_server_requests_5xx",
                            "Rapid burst of HTTP 500 internal server errors detected on customer checkout endpoints."
                    );
                    createdIncidentId = inc.getId();

                    activeScenarios.put(scenarioId, new ActiveFailureScenario(
                            scenarioId,
                            "ERROR_SPIKE",
                            targetService,
                            "ERROR_SPIKE",
                            null,
                            "High frequency 500 internal server error spike",
                            Instant.now(),
                            "ACTIVE"
                    ));
                }

                case "DEPLOYMENT_REGRESSION" -> {
                    String version = (request.version() != null && !request.version().isBlank()) ? request.version() : "v2.5.0-regression";
                    Map<String, Object> metadataMap = Map.of(
                            "changeType", "CONFIG_CHANGE",
                            "commit", "a8f9c1d",
                            "issue", "Incompatible schema migration"
                    );
                    ProcessedDeploymentEvent dep = new ProcessedDeploymentEvent(
                            "dep-" + scenarioId,
                            "DEPLOYMENT_COMPLETED",
                            targetService,
                            version,
                            Instant.now(),
                            "trace-" + scenarioId,
                            "{\"changeType\":\"CONFIG_CHANGE\",\"commit\":\"a8f9c1d\",\"issue\":\"Incompatible schema migration\"}",
                            Instant.now()
                    );
                    deploymentService.processEvent(new DeploymentEvent(
                            dep.getEventId(),
                            dep.getEventType(),
                            dep.getService(),
                            dep.getVersion(),
                            dep.getTimestamp(),
                            dep.getTraceId(),
                            metadataMap
                    ));

                    Incident inc = createSimulatedIncident(
                            "Post-Deployment Regression: " + version + " on " + targetService,
                            AnomalySeverity.HIGH,
                            targetService,
                            targetService,
                            Set.of(targetService, "order-service"),
                            "deployment_version_mismatch",
                            "Post-release regression detected following deployment of version " + version + " with schema incompatibility."
                    );
                    createdIncidentId = inc.getId();

                    activeScenarios.put(scenarioId, new ActiveFailureScenario(
                            scenarioId,
                            "DEPLOYMENT_REGRESSION",
                            targetService,
                            "DEPLOYMENT_REGRESSION",
                            null,
                            String.format("Deployed %s with config regression on %s", version, targetService),
                            Instant.now(),
                            "ACTIVE"
                    ));
                }

                default -> {
                    return ResponseEntity.badRequest().body(Map.of("error", "Unknown scenario: " + scenario));
                }
            }

            ActiveFailureScenario result = activeScenarios.get(scenarioId);
            Map<String, Object> response = new ConcurrentHashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Failure scenario successfully activated and logged to Incident Stream.");
            response.put("scenario", result);
            if (createdIncidentId != null) {
                response.put("incidentId", createdIncidentId);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.warn("Failed to inject failure scenario '{}': {}", scenario, e.getMessage());
            ActiveFailureScenario simResult = new ActiveFailureScenario(
                    scenarioId,
                    scenario,
                    targetService,
                    scenario,
                    request.latencyMs(),
                    request.description() != null ? request.description() : "Simulated failure injection",
                    Instant.now(),
                    "ACTIVE (Simulated)"
            );
            activeScenarios.put(scenarioId, simResult);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS_SIMULATED",
                    "message", "Failure scenario activated in demonstration mode: " + e.getMessage(),
                    "scenario", simResult
            ));
        }
    }

    private Incident createSimulatedIncident(
            String title,
            AnomalySeverity severity,
            String primaryService,
            String rootService,
            Set<String> affected,
            String metric,
            String description) {
        Incident inc = new Incident();
        inc.setIncidentId("INC-CHAOS-" + UUID.randomUUID().toString().substring(0, 8));
        inc.setTitle(title);
        inc.setSeverity(severity);
        inc.setStatus(IncidentStatus.OPEN);
        inc.setPrimaryService(primaryService);
        inc.setRootService(rootService);
        inc.setAffectedServices(affected != null ? new HashSet<>(affected) : new HashSet<>());
        inc.setStartedAt(Instant.now().minusSeconds(60));
        inc.setDetectedAt(Instant.now());
        inc.setLastEventAt(Instant.now());
        inc.setMetric(metric);
        inc.setDescription(description);
        inc.setFingerprint(primaryService + ":" + metric);
        return incidentRepository.save(inc);
    }

    @DeleteMapping("/active/{id}")
    public ResponseEntity<?> disableSpecificFailure(@PathVariable String id) {
        ActiveFailureScenario removed = activeScenarios.remove(id);
        if (removed != null) {
            // Attempt cleanup on microservices
            try {
                if ("payment-service".equalsIgnoreCase(removed.service())) {
                    resetServiceFailure(paymentServiceUrl);
                } else if ("inventory-service".equalsIgnoreCase(removed.service())) {
                    resetServiceFailure(inventoryServiceUrl);
                } else if ("order-service".equalsIgnoreCase(removed.service())) {
                    resetServiceFailure(orderServiceUrl);
                }
            } catch (Exception ignored) {}

            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Failure scenario disabled: " + id));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetAllFailures() {
        activeScenarios.clear();
        try {
            resetServiceFailure(paymentServiceUrl);
            resetServiceFailure(inventoryServiceUrl);
            resetServiceFailure(orderServiceUrl);
        } catch (Exception ignored) {}

        log.info("Reset all active failure injections to normal baseline operational state.");
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "All active failure injections cleared. All microservices returned to nominal operational state."
        ));
    }

    private void callServiceFailureApi(String baseUrl, String failureType, Long latencyMs) {
        try {
            Map<String, Object> body = latencyMs != null
                    ? Map.of("type", failureType, "latencyMs", latencyMs)
                    : Map.of("type", failureType);

            restClient.post()
                    .uri(baseUrl + "/internal/failures")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.debug("Notice: Failed to dispatch to microservice endpoint {}/internal/failures: {}", baseUrl, e.getMessage());
            throw new RuntimeException("Could not connect to service endpoint: " + e.getMessage(), e);
        }
    }

    private void resetServiceFailure(String baseUrl) {
        try {
            restClient.delete()
                    .uri(baseUrl + "/internal/failures")
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {}
    }
}
