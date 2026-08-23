package com.aiincident.logprocessor.dependency;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-backed service dependency graph manager.
 * Represents, stores, and queries microservice call graphs and downstream/upstream relationships.
 */
@Service
public class ServiceDependencyService {

    private static final Logger log = LoggerFactory.getLogger(ServiceDependencyService.class);

    private final ServiceDependencyRepository repository;

    public ServiceDependencyService(ServiceDependencyRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void initDefaultDependencies() {
        // Seed initial platform dependencies if not already present:
        // Order -> Payment
        // Payment -> Inventory
        // Payment -> PostgreSQL
        // Order -> Inventory
        // Inventory -> PostgreSQL
        ensureDependency("order-service", "payment-service", ServiceDependencyType.HTTP_REST, "Order service calls payment service for payment processing");
        ensureDependency("payment-service", "inventory-service", ServiceDependencyType.HTTP_REST, "Payment service calls inventory service");
        ensureDependency("payment-service", "postgres", ServiceDependencyType.DATABASE, "Payment service persists to PostgreSQL database");
        ensureDependency("payment-service", "database", ServiceDependencyType.DATABASE, "Payment service database connection");
        ensureDependency("order-service", "inventory-service", ServiceDependencyType.HTTP_REST, "Order service calls inventory service for stock checks");
        ensureDependency("inventory-service", "postgres", ServiceDependencyType.DATABASE, "Inventory service persists to PostgreSQL database");
        ensureDependency("inventory-service", "database", ServiceDependencyType.DATABASE, "Inventory service database connection");
    }

    @Transactional
    public ServiceDependency ensureDependency(String source, String target, ServiceDependencyType type, String description) {
        String src = source != null ? source.toLowerCase().trim() : "unknown";
        String tgt = target != null ? target.toLowerCase().trim() : "unknown";

        Optional<ServiceDependency> existing = repository.findBySourceServiceAndTargetService(src, tgt);
        if (existing.isPresent()) {
            return existing.get();
        }

        ServiceDependency dep = new ServiceDependency(src, tgt, type, "HIGH", description);
        ServiceDependency saved = repository.save(dep);
        log.info("Registered service dependency: {} -> {} [{}]", src, tgt, type);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ServiceDependency> getAllDependencies() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ServiceDependency> getDownstream(String service) {
        if (service == null || service.isBlank()) {
            return List.of();
        }
        return repository.findBySourceService(service.toLowerCase().trim());
    }

    @Transactional(readOnly = true)
    public List<ServiceDependency> getUpstream(String service) {
        if (service == null || service.isBlank()) {
            return List.of();
        }
        return repository.findByTargetService(service.toLowerCase().trim());
    }

    @Transactional(readOnly = true)
    public ServiceTopology getServiceTopology(String service) {
        if (service == null || service.isBlank()) {
            return new ServiceTopology("unknown", Set.of(), Set.of(), Set.of(), List.of());
        }

        String canonical = service.toLowerCase().trim();
        List<ServiceDependency> downstreamDeps = repository.findBySourceService(canonical);
        List<ServiceDependency> upstreamDeps = repository.findByTargetService(canonical);

        Set<String> downstream = new HashSet<>();
        for (ServiceDependency d : downstreamDeps) {
            downstream.add(d.getTargetService());
        }

        Set<String> upstream = new HashSet<>();
        for (ServiceDependency u : upstreamDeps) {
            upstream.add(u.getSourceService());
        }

        Set<String> allRelated = new HashSet<>();
        allRelated.add(canonical);
        allRelated.addAll(downstream);
        allRelated.addAll(upstream);

        List<ServiceDependency> allDirect = new ArrayList<>(downstreamDeps);
        allDirect.addAll(upstreamDeps);

        return new ServiceTopology(canonical, downstream, upstream, allRelated, allDirect);
    }

    @Transactional(readOnly = true)
    public boolean areServicesRelated(String serviceA, String serviceB) {
        if (serviceA == null || serviceB == null) {
            return false;
        }

        String a = serviceA.toLowerCase().trim();
        String b = serviceB.toLowerCase().trim();

        if (a.equals(b)) {
            return true;
        }

        List<ServiceDependency> allDeps = repository.findAll();

        Set<String> visited = new HashSet<>();
        Set<String> queue = new HashSet<>();
        queue.add(a);

        while (!queue.isEmpty()) {
            String curr = queue.iterator().next();
            queue.remove(curr);
            visited.add(curr);

            for (ServiceDependency dep : allDeps) {
                if (dep.getSourceService().equalsIgnoreCase(curr)) {
                    String target = dep.getTargetService();
                    if (target.equalsIgnoreCase(b)) {
                        return true;
                    }
                    if (!visited.contains(target)) {
                        queue.add(target);
                    }
                }
                if (dep.getTargetService().equalsIgnoreCase(curr)) {
                    String source = dep.getSourceService();
                    if (source.equalsIgnoreCase(b)) {
                        return true;
                    }
                    if (!visited.contains(source)) {
                        queue.add(source);
                    }
                }
            }
        }

        return false;
    }

    @Transactional
    public ServiceDependency addDependency(String source, String target, ServiceDependencyType type, String description) {
        String src = source != null ? source.toLowerCase().trim() : "unknown";
        String tgt = target != null ? target.toLowerCase().trim() : "unknown";

        Optional<ServiceDependency> existing = repository.findBySourceServiceAndTargetService(src, tgt);
        if (existing.isPresent()) {
            ServiceDependency dep = existing.get();
            if (type != null) dep.setDependencyType(type);
            if (description != null) dep.setDescription(description);
            dep.setUpdatedAt(Instant.now());
            return repository.save(dep);
        }

        ServiceDependency dep = new ServiceDependency(src, tgt, type, "HIGH", description);
        return repository.save(dep);
    }

    @Transactional
    public boolean removeDependency(String source, String target) {
        if (source == null || target == null) {
            return false;
        }
        String src = source.toLowerCase().trim();
        String tgt = target.toLowerCase().trim();

        if (repository.existsBySourceServiceAndTargetService(src, tgt)) {
            repository.deleteBySourceServiceAndTargetService(src, tgt);
            log.info("Removed service dependency: {} -> {}", src, tgt);
            return true;
        }
        return false;
    }

    public record ServiceTopology(
            String service,
            Set<String> downstream,
            Set<String> upstream,
            Set<String> allRelated,
            List<ServiceDependency> directDependencies
    ) {}
}
