package com.aiincident.logprocessor.historical;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Curated canonical operational runbooks covering the 8 major operational failure domains.
 */
public final class RunbookDataset {

    private RunbookDataset() {
    }

    public static List<Runbook> getCanonicalRunbooks() {
        List<Runbook> list = new ArrayList<>();

        // 1. DATABASE CONNECTION EXHAUSTION
        list.add(new Runbook(
                "RB-DB-001",
                "Database Connection Pool Exhaustion & Thread Starvation Remediation",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                AnomalySeverity.CRITICAL,
                Set.of("payment-service", "order-service", "inventory-service", "postgres"),
                List.of(
                        "HikariPool connection timeout after configured lease timeout",
                        "Spike in HTTP 500 / DB_TIMEOUT operational log events",
                        "PostgreSQL pg_stat_activity showing active connections at max_connections limit",
                        "Thread dump showing worker threads blocked on getConnection()"
                ),
                List.of(
                        "Verify kubectl access to target namespace and psql access to PostgreSQL database",
                        "Confirm Grafana HikariCP dashboard metrics for active, idle, and pending threads"
                ),
                List.of(
                        "Inspect active database queries using: SELECT pid, state, age(clock_timestamp(), query_start), query FROM pg_stat_activity WHERE state != 'idle' ORDER BY query_start ASC LIMIT 10;",
                        "Identify blocking transaction locks via: SELECT blocked_locks.pid AS blocked_pid, blocking_locks.pid AS blocking_pid, blocking_activity.query AS blocking_statement FROM pg_catalog.pg_locks blocked_locks JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid AND blocking_locks.pid != blocked_locks.pid JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid WHERE NOT blocked_locks.granted;",
                        "Terminate runaway blocking or idle-in-transaction sessions: SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE state = 'idle in transaction' AND age(clock_timestamp(), state_change) > interval '2 minutes';",
                        "If traffic surge exceeds capacity, dynamically scale maximum Hikari pool size and enable connection leak detection: spring.datasource.hikari.maximum-pool-size=50 and leak-detection-threshold=5000ms.",
                        "If database CPU is pegged, provision read-replica offloading for read-heavy queries."
                ),
                List.of(
                        "Verify HikariCP pending connection acquisition queue drops to 0",
                        "Confirm HTTP 500 error rate drops below 1% on affected services",
                        "Ensure database active connection count stabilizes below 70% of max_connections"
                ),
                "If connection starvation persists > 5 minutes after terminating rogue queries, page DBA On-Call and Lead Backend Architect.",
                null,
                Set.of("database", "postgres", "hikaricp", "pool-exhaustion", "locking"),
                Instant.parse("2026-08-01T00:00:00Z")
        ));

        // 2. DEPLOYMENT REGRESSION
        list.add(new Runbook(
                "RB-DEPLOY-001",
                "Deployment Regression & Automated Container Rollback Procedure",
                HistoricalIncidentCategory.DEPLOYMENT_REGRESSION,
                AnomalySeverity.CRITICAL,
                Set.of("order-service", "payment-service", "inventory-service"),
                List.of(
                        "Error rate spike immediately following DEPLOYMENT_COMPLETED event",
                        "Uncaught SerializationException / NoSuchMethodError / ClassCastException in service logs",
                        "CrashLoopBackOff or FailedReadinessProbe on newly deployed pods",
                        "HTTP 400/500 spikes on critical user business transactions"
                ),
                List.of(
                        "Locate the commit hash and deployment version identifier in the deployment pipeline",
                        "Confirm access to Kubernetes cluster via kubectl or ArgoCD deployment interface"
                ),
                List.of(
                        "Inspect recent deployment revision status: kubectl rollout history deployment/<service-name> -n production",
                        "Immediately initiate rollback to the previous known-good revision: kubectl rollout undo deployment/<service-name> -n production",
                        "Verify that newly spawned pods from previous version achieve Ready status: kubectl rollout status deployment/<service-name> -n production",
                        "If configuration/secret mismatch is the cause, verify Secret/ConfigMap values against Vault and redeploy with corrected keys.",
                        "Inspect Jackson serialization / DTO contracts for backward compatibility breaks."
                ),
                List.of(
                        "Verify order/payment error rate returns to zero on /api/metrics summary",
                        "Execute synthetic health check transaction against the service REST API",
                        "Confirm no further 400 Bad Request or deserialization errors appear in operational logs"
                ),
                "If rollback fails or error persists across previous version due to permanent schema change, escalate to Release Manager and Core Platform Lead.",
                null,
                Set.of("deployment", "rollback", "regression", "kubernetes", "argocd"),
                Instant.parse("2026-08-01T00:00:00Z")
        ));

        // 3. SERVICE UNAVAILABLE
        list.add(new Runbook(
                "RB-UNAVAIL-001",
                "Service Unavailable (503), JVM OOM Panic & Crash Recovery",
                HistoricalIncidentCategory.SERVICE_UNAVAILABLE,
                AnomalySeverity.CRITICAL,
                Set.of("order-service", "payment-service", "inventory-service"),
                List.of(
                        "HTTP 503 Service Unavailable returned to API Gateway and callers",
                        "Container exit code 137 (OOMKilled) in Kubernetes pod status",
                        "Continuous CrashLoopBackOff on service replicas",
                        "Upstream circuit breakers transitioning to OPEN state"
                ),
                List.of(
                        "Check Kubernetes pod events: kubectl describe pod -l app=<service-name>",
                        "Inspect container memory metrics and JVM heap dump location"
                ),
                List.of(
                        "Temporarily scale horizontal pod replicas to distribute incoming traffic load: kubectl scale deployment/<service-name> --replicas=6",
                        "If pods are killed by OOM (Exit code 137), increase container memory request/limit in deployment manifest by 50% (e.g. 2Gi -> 3Gi) and tune JVM -XX:MaxRAMPercentage=75.",
                        "Identify if a heavy batch query or bulk sync is causing heap blowup; temporarily disable scheduled batch jobs.",
                        "Check PodDisruptionBudgets to ensure node maintenance does not drain all replicas concurrently.",
                        "Verify Kubernetes readiness probe settings (timeoutSeconds >= 3, failureThreshold >= 3) to prevent probe flapping."
                ),
                List.of(
                        "Confirm all replicas show 1/1 Running in kubectl get pods",
                        "Verify HTTP 503 error rate drops to 0% in metrics aggregation layer",
                        "Confirm upstream circuit breakers recover to CLOSED status"
                ),
                "If container crashes persist across increased memory allocations, page SRE Incident Commander and Service Owner.",
                null,
                Set.of("service-unavailable", "503", "oom", "crashloop", "resilience"),
                Instant.parse("2026-08-01T00:00:00Z")
        ));

        // 4. NETWORK LATENCY
        list.add(new Runbook(
                "RB-NET-001",
                "Inter-Service Network Latency, DNS Degradation & Cross-AZ Congestion",
                HistoricalIncidentCategory.NETWORK_LATENCY,
                AnomalySeverity.HIGH,
                Set.of("order-service", "payment-service", "inventory-service"),
                List.of(
                        "Spike in average and p95/p99 request latency without corresponding error rate surge",
                        "High TCP connection establishment latency or intermittent SocketTimeoutException",
                        "CoreDNS query latency exceeding 500ms or DNS packet drops",
                        "NAT Gateway SNAT port allocation errors"
                ),
                List.of(
                        "Check cloud provider regional health dashboard for network infrastructure alerts",
                        "Verify CoreDNS cluster metrics in Prometheus/Grafana"
                ),
                List.of(
                        "Verify CoreDNS pod resource utilization and scale CoreDNS deployment if CPU > 80%: kubectl scale deployment/coredns -n kube-system --replicas=8",
                        "Enable NodeLocal DNSCache daemonset to avoid cluster-wide DNS lookup contention on worker nodes.",
                        "If cross-AZ transit link degradation is occurring, enable Kubernetes topology-aware routing (topology.kubernetes.io/zone hints) to keep traffic local.",
                        "Verify HTTP client connection pooling (keep-alive, maxIdleTime, maxConnectionsPerRoute >= 50) to avoid excessive ephemeral port churn.",
                        "If NAT Gateway SNAT exhaustion is detected, allocate secondary egress IP addresses to NAT Gateway."
                ),
                List.of(
                        "Check /api/metrics for latencyAvg and p95 returning to baseline (<100ms)",
                        "Confirm CoreDNS lookup latency drops below 2ms",
                        "Verify no TCP retransmission alarms active on VPC network interfaces"
                ),
                "If network latency is caused by underlying cloud provider transit link failure, notify Cloud Operations and enable multi-region failover.",
                null,
                Set.of("network", "latency", "dns", "coredns", "topology", "snat"),
                Instant.parse("2026-08-01T00:00:00Z")
        ));

        // 5. MEMORY PRESSURE
        list.add(new Runbook(
                "RB-MEM-001",
                "JVM Heap Memory Pressure, Full GC Pauses & Leak Mitigation",
                HistoricalIncidentCategory.MEMORY_PRESSURE,
                AnomalySeverity.HIGH,
                Set.of("order-service", "payment-service", "inventory-service"),
                List.of(
                        "JVM Old Generation heap occupancy exceeding 90%",
                        "Consecutive Full Garbage Collection pauses > 3000ms",
                        "Gradual memory creep over hours/days leading to Metaspace or Heap OOM",
                        "Application latency spikes correlated with GC pause events"
                ),
                List.of(
                        "Capture JVM heap histogram and thread dump: jcmd <pid> GC.heap_info or jmap -histo:live <pid>",
                        "Inspect Prometheus JVM memory pool metrics (jvm_memory_used_bytes)"
                ),
                List.of(
                        "Trigger rolling restart of application instances to relieve acute memory pressure: kubectl rollout restart deployment/<service-name>",
                        "Check for unbounded in-memory caches (ConcurrentHashMap, static collections); configure Caffeine/Guava caches with maximumSize and expireAfterWrite policies.",
                        "Inspect HTTP filters and interceptors for missing ThreadLocal.remove() invocations in finally blocks.",
                        "Switch large file/report exports from DOM-based representations to disk-buffered streaming (e.g. streaming JSON or SXSSF workbooks).",
                        "If legitimate workload growth requires more heap, update container memory limits and JVM -Xmx."
                ),
                List.of(
                        "Verify Old Gen memory stabilizes below 60% after major GC cycles",
                        "Confirm Full GC pause duration drops below 50ms in JVM metrics",
                        "Verify zero OutOfMemoryError occurrences in application logs"
                ),
                "If heap leak root cause is unknown and memory continues growing after restart, capture a full heap dump (.hprof) and escalate to Performance Engineering Lead.",
                null,
                Set.of("memory", "jvm", "heap", "gc-pause", "threadlocal", "leak"),
                Instant.parse("2026-08-01T00:00:00Z")
        ));

        // 6. CACHE FAILURE
        list.add(new Runbook(
                "RB-CACHE-001",
                "Cache Failure, Redis Stampede Storm & Stale Data Invalidation",
                HistoricalIncidentCategory.CACHE_FAILURE,
                AnomalySeverity.HIGH,
                Set.of("inventory-service", "payment-service", "postgres"),
                List.of(
                        "Redis cache hit ratio dropping below 50% rapidly",
                        "Sudden 10x read query surge hitting PostgreSQL database primary",
                        "Cache node memory eviction spikes or Redis connection timeouts",
                        "Stale stock or inconsistent data served to downstream ordering services"
                ),
                List.of(
                        "Verify Redis cluster status and cluster nodes health via redis-cli cluster info",
                        "Check database query throughput in pg_stat_statements"
                ),
                List.of(
                        "If cache keys expired concurrently (TTL stampede), deploy single-flight mutex locking on cache misses so only 1 worker queries DB per key.",
                        "Introduce randomized jitter (e.g. base TTL ± 20%) to key expiration policies to prevent synchronized expiration waves.",
                        "If Redis cluster encountered failover, execute automated cache pre-warming scripts for hot catalog keys.",
                        "If multi-pod local in-memory caches are out of sync, trigger Redis Pub/Sub cache invalidation broadcasts or disable local caching.",
                        "If Redis memory limit is reached (OOM command not allowed), increase maxmemory and ensure volatile-lru eviction policy is enabled."
                ),
                List.of(
                        "Confirm Redis cache hit ratio recovers to >= 95%",
                        "Verify PostgreSQL read QPS and CPU usage drop back to normal baseline",
                        "Ensure data consistency across all service pods"
                ),
                "If Redis cluster is unavailable and database is at risk of collapse, enable API-level rate limiting and page Database Infrastructure Team.",
                null,
                Set.of("cache", "redis", "stampede", "thundering-herd", "invalidation"),
                Instant.parse("2026-08-01T00:00:00Z")
        ));

        // 7. DEPENDENCY TIMEOUT
        list.add(new Runbook(
                "RB-TIMEOUT-001",
                "Downstream Dependency Timeout & Circuit Breaker Mitigation",
                HistoricalIncidentCategory.DEPENDENCY_TIMEOUT,
                AnomalySeverity.HIGH,
                Set.of("order-service", "payment-service", "inventory-service"),
                List.of(
                        "ResourceAccessException / SocketTimeoutException when calling downstream service",
                        "Upstream service HTTP request processing threads queued or exhausted",
                        "Third-party external integration (payment gateway, tax, shipping) unresponsive",
                        "Cascading latency and timeout propagation up the call graph"
                ),
                List.of(
                        "Identify failing downstream service endpoint in distributed traces or error logs",
                        "Inspect downstream service health and response latency percentiles"
                ),
                List.of(
                        "Verify client connect and socket read timeout configurations (connectTimeout <= 2s, readTimeout <= 3s).",
                        "Ensure Circuit Breaker (Resilience4j) is configured for external dependencies with slidingWindowSize=20, failureRateThreshold=50%, and waitDurationInOpenState=10s.",
                        "If primary third-party provider is degraded, activate automated failover to secondary provider (e.g. backup payment gateway).",
                        "For non-critical path dependencies (e.g. tax calculation, recommendation feeds), enable asynchronous fallback or cached estimation.",
                        "If downstream internal microservice is saturated, scale downstream replicas and enable request rate limiting."
                ),
                List.of(
                        "Verify upstream request latency returns to normal threshold",
                        "Confirm circuit breaker transitions from OPEN -> HALF_OPEN -> CLOSED",
                        "Verify successful completion rate on business transactions"
                ),
                "If critical downstream dependency remains down without viable fallback, activate maintenance degradation mode and notify Product Stakeholders.",
                null,
                Set.of("dependency", "timeout", "circuit-breaker", "resilience4j", "fallback"),
                Instant.parse("2026-08-01T00:00:00Z")
        ));

        // 8. MESSAGE PROCESSING FAILURE
        list.add(new Runbook(
                "RB-MSG-001",
                "Kafka Consumer Lag, Poison Pill Payloads & Rebalance Storm Recovery",
                HistoricalIncidentCategory.MESSAGE_PROCESSING_FAILURE,
                AnomalySeverity.HIGH,
                Set.of("log-processor", "payment-service", "kafka"),
                List.of(
                        "Kafka consumer lag growing continuously across partition offsets",
                        "DeserializationException / RecordDeserializationException in consumer logs",
                        "Frequent consumer group rebalances (RebalanceInProgressException)",
                        "Real-time event processing delay exceeding SLA"
                ),
                List.of(
                        "Inspect consumer lag via kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group <group-name>",
                        "Check consumer log stream for specific failing message offset and partition"
                ),
                List.of(
                        "If a malformed payload (poison pill) is blocking offset commit, configure ErrorHandlingDeserializer and route failing records to Dead Letter Topic (.DLT).",
                        "If consumer rebalance storms are occurring due to slow record processing, decrease max.poll.records (e.g. 500 -> 50) or increase max.poll.interval.ms.",
                        "Offload heavy processing inside the Kafka listener to an asynchronous worker thread pool executor.",
                        "If throughput exceeds single consumer capacity, scale consumer group instances up to the topic partition count.",
                        "If schema evolution introduced unknown enum values, configure Jackson READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE."
                ),
                List.of(
                        "Verify consumer lag decreases steadily towards 0 records",
                        "Confirm consumer group status shows Stable without active rebalancing",
                        "Verify Dead Letter Topic captures bad records without stalling main pipeline"
                ),
                "If Kafka broker leader partitions are under-replicated or broker storage is exhausted, page Kafka Platform On-Call immediately.",
                null,
                Set.of("kafka", "messaging", "consumer-lag", "poison-pill", "rebalance", "dlq"),
                Instant.parse("2026-08-01T00:00:00Z")
        ));

        return list;
    }
}
