package com.aiincident.logprocessor.incident;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic service dependency topology model.
 * Maps callers and callees to recognize cross-service cascading failures without AI.
 */
@Component
public class ServiceDependencyGraph {

    // Adjacency list: service -> downstream dependencies called by that service
    private final Map<String, Set<String>> downstreamDependencies = new HashMap<>();
    // Reverse adjacency list: service -> upstream callers that depend on this service
    private final Map<String, Set<String>> upstreamCallers = new HashMap<>();

    public ServiceDependencyGraph() {
        // Default microservice architecture topologies in this platform:
        // order-service calls payment-service and inventory-service
        addDependency("order-service", "payment-service");
        addDependency("order-service", "inventory-service");
        addDependency("payment-service", "postgres");
        addDependency("payment-service", "database");
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

        String a = serviceA.toLowerCase().trim();
        String b = serviceB.toLowerCase().trim();

        if (a.equals(b)) {
            return true;
        }

        // Direct downstream or upstream
        if (getDownstream(a).contains(b) || getUpstream(a).contains(b)) {
            return true;
        }
        if (getDownstream(b).contains(a) || getUpstream(b).contains(a)) {
            return true;
        }

        // Check transitive dependencies
        return isTransitivelyConnected(a, b);
    }

    public Set<String> getDownstream(String service) {
        return downstreamDependencies.getOrDefault(service.toLowerCase().trim(), Collections.emptySet());
    }

    public Set<String> getUpstream(String service) {
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
