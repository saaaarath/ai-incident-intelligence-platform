package com.aiincident.logprocessor.controller;

import com.aiincident.logging.deployment.DeploymentEvent;
import com.aiincident.logging.deployment.DeploymentPublisher;
import com.aiincident.logprocessor.entity.ProcessedDeploymentEvent;
import com.aiincident.logprocessor.service.DeploymentProcessorService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

    private final DeploymentProcessorService deploymentProcessorService;
    private final DeploymentPublisher deploymentPublisher;

    public DeploymentController(
            DeploymentProcessorService deploymentProcessorService,
            @Autowired(required = false) DeploymentPublisher deploymentPublisher) {
        this.deploymentProcessorService = deploymentProcessorService;
        this.deploymentPublisher = deploymentPublisher;
    }

    @PostMapping
    public ResponseEntity<?> recordDeployment(@RequestBody DeploymentEvent request) {
        String eventId = (request.eventId() != null && !request.eventId().isBlank()) ? request.eventId().trim() : UUID.randomUUID().toString();
        Instant timestamp = request.timestamp() != null ? request.timestamp() : Instant.now();

        DeploymentEvent event = new DeploymentEvent(
                eventId,
                request.eventType(),
                request.service(),
                request.version(),
                timestamp,
                request.traceId(),
                request.metadata()
        );

        if (deploymentPublisher != null) {
            deploymentPublisher.publish(event);
        }

        // Also process locally to ensure immediate persistence return
        Optional<ProcessedDeploymentEvent> result = deploymentProcessorService.processEvent(event);
        if (result.isPresent()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.get());
        } else {
            return ResponseEntity.badRequest().body("Invalid deployment event format or missing required fields");
        }
    }

    @GetMapping
    public ResponseEntity<List<ProcessedDeploymentEvent>> getDeployments(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String traceId) {
        if (service != null && !service.isBlank()) {
            return ResponseEntity.ok(deploymentProcessorService.findByService(service.trim()));
        }
        if (version != null && !version.isBlank()) {
            return ResponseEntity.ok(deploymentProcessorService.findByVersion(version.trim()));
        }
        if (eventType != null && !eventType.isBlank()) {
            return ResponseEntity.ok(deploymentProcessorService.findByEventType(eventType.trim()));
        }
        if (traceId != null && !traceId.isBlank()) {
            return ResponseEntity.ok(deploymentProcessorService.findByTraceId(traceId.trim()));
        }
        return ResponseEntity.ok(deploymentProcessorService.findAllDeployments());
    }

    @GetMapping("/service/{service}")
    public ResponseEntity<List<ProcessedDeploymentEvent>> getDeploymentsByService(@PathVariable String service) {
        return ResponseEntity.ok(deploymentProcessorService.findByService(service));
    }
}
