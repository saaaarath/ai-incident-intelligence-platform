package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.dependency.ServiceDependency;
import com.aiincident.logprocessor.dependency.ServiceDependencyService;
import com.aiincident.logprocessor.dependency.ServiceDependencyType;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/dependencies", "/api/dependencies"})
public class DependencyController {

    private final ServiceDependencyService dependencyService;

    public DependencyController(ServiceDependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    /**
     * Retrieve all service dependency relationships.
     * Example: GET /api/dependencies
     */
    @GetMapping
    public ResponseEntity<List<ServiceDependency>> getAllDependencies() {
        return ResponseEntity.ok(dependencyService.getAllDependencies());
    }

    /**
     * Retrieve topological dependencies and callers for a specific service.
     * Example: GET /api/dependencies/order-service
     */
    @GetMapping("/{service}")
    public ResponseEntity<ServiceDependencyService.ServiceTopology> getServiceTopology(@PathVariable String service) {
        ServiceDependencyService.ServiceTopology topology = dependencyService.getServiceTopology(service);
        return ResponseEntity.ok(topology);
    }

    /**
     * Retrieve downstream services called by the specified service.
     * Example: GET /api/dependencies/payment-service/downstream
     */
    @GetMapping("/{service}/downstream")
    public ResponseEntity<List<ServiceDependency>> getDownstreamDependencies(@PathVariable String service) {
        return ResponseEntity.ok(dependencyService.getDownstream(service));
    }

    /**
     * Retrieve upstream callers that depend on the specified service.
     * Example: GET /api/dependencies/payment-service/upstream
     */
    @GetMapping("/{service}/upstream")
    public ResponseEntity<List<ServiceDependency>> getUpstreamCallers(@PathVariable String service) {
        return ResponseEntity.ok(dependencyService.getUpstream(service));
    }

    /**
     * Register or update a service dependency.
     * Example: POST /api/dependencies
     */
    @PostMapping
    public ResponseEntity<ServiceDependency> createDependency(@RequestBody DependencyRequest request) {
        if (request == null || request.sourceService() == null || request.targetService() == null) {
            return ResponseEntity.badRequest().build();
        }
        ServiceDependencyType type = request.dependencyType() != null ? request.dependencyType() : ServiceDependencyType.HTTP_REST;
        ServiceDependency saved = dependencyService.ensureDependency(
                request.sourceService(),
                request.targetService(),
                type,
                request.description()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Remove a service dependency link.
     * Example: DELETE /api/dependencies?source=order-service&target=payment-service
     */
    @DeleteMapping
    public ResponseEntity<Void> removeDependency(
            @RequestParam String source,
            @RequestParam String target) {
        boolean removed = dependencyService.removeDependency(source, target);
        if (removed) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    public record DependencyRequest(
            String sourceService,
            String targetService,
            ServiceDependencyType dependencyType,
            String description
    ) {}
}
