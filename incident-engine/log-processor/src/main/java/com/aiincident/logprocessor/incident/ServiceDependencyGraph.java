package com.aiincident.logprocessor.incident;

import com.aiincident.logprocessor.dependency.ServiceDependency;
import com.aiincident.logprocessor.dependency.ServiceDependencyService;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Deterministic service dependency topology model.
 * Maps callers and callees to recognize cross-service cascading failures without AI.
 */
@Component
public class ServiceDependencyGraph {

    private final ServiceDependencyService dependencyService;

    // In-memory fallback adjacency lists:
    private final Map<String, Set<String>> downstreamDependencies = new HashMap<>();
    private final Map<String, Set<String>> upstreamCallers = new HashMap<>();

    public ServiceDependencyGraph() {
        this(null);
    }

    @Autowired
    public ServiceDependencyGraph(@Autowired(required = false) ServiceDependencyService dependencyService) {
        this.dependencyService = dependencyService;

        // Default initial dependencies
        addDependency("order-service", "payment-service");
        addDependency("payment-service", "inventory-service");
        addDependency("payment-service", "postgres");
        addDependency("payment-service", "database");
        addDependency("order-service", "inventory-service");
        addDependency("inventory-service", "postgres");
        addDependency("inventory-service", "database");
    }

    public synchronized void addDependency(String caller, String callee) {
        String from = caller.toLowerCase().trim();
        String to = callee.toLowerCase().trim();

        downstreamDependencies.computeIfAbsent(from, k -> new HashSet<>()).add(to);
        upstreamCallers.computeIfAbsent(to, k -> new HashSet<>()).add(from);
    }

    /**
     * Check whether service A and service B are directly or transitively related in the dependency graph.
     */
    public boolean areServicesRelated(String serviceA, String serviceB) {
        if (serviceA == null || serviceB == null) {
            return false;
        }

        if (dependencyService != null) {
            return dependencyService.areServicesRelated(serviceA, serviceB);
        }

        String a = serviceA.toLowerCase().trim();
        String b = serviceB.toLowerCase().trim();

        if (a.equals(b)) {
            return true;
        }

        if (getDownstream(a).contains(b) || getUpstream(a).contains(b)) {
            return true;
        }
        if (getDownstream(b).contains(a) || getUpstream(b).contains(a)) {
            return true;
        }

        return isTransitivelyConnected(a, b);
    }

    public Set<String> getDownstream(String service) {
        if (dependencyService != null) {
            List<ServiceDependency> list = dependencyService.getDownstream(service);
            Set<String> set = new HashSet<>();
            for (ServiceDependency d : list) {
                set.add(d.getTargetService());
            }
            return set;
        }
        return downstreamDependencies.getOrDefault(service.toLowerCase().trim(), Collections.emptySet());
    }

    public Set<String> getUpstream(String service) {
        if (dependencyService != null) {
            List<ServiceDependency> list = dependencyService.getUpstream(service);
            Set<String> set = new HashSet<>();
            for (ServiceDependency d : list) {
                set.add(d.getSourceService());
            }
            return set;
        }
        return upstreamCallers.getOrDefault(service.toLowerCase().trim(), Collections.emptySet());
    }

    private boolean isTransitivelyConnected(String source, String target) {
        Set<String> visited = new HashSet<>();
        Set<String> queue = new HashSet<>();
        queue.add(source);

        while (!queue.isEmpty()) {
            String curr = queue.iterator().next();
            queue.remove(curr);
            visited.add(curr);

            Set<String> neighbors = new HashSet<>(getDownstream(curr));
            neighbors.addAll(getUpstream(curr));

            for (String neighbor : neighbors) {
                if (neighbor.equals(target)) {
                    return true;
                }
                if (!visited.contains(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return false;
    }
}
