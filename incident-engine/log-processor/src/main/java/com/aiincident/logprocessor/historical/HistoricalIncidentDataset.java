package com.aiincident.logprocessor.historical;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Curated historical operational knowledge base containing real-world incident post-mortems across 8 failure categories.
 */
public final class HistoricalIncidentDataset {

    private HistoricalIncidentDataset() {
    }

    public static List<HistoricalIncident> getCanonicalIncidents() {
        List<HistoricalIncident> list = new ArrayList<>();

        // =========================================================================
        // CATEGORY 1: DATABASE CONNECTION EXHAUSTION (3 Incidents)
        // =========================================================================
        list.add(new HistoricalIncident(
                "HIST-INC-001",
                "Payment Service HikariCP Connection Pool Saturation During Flash Sale",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                AnomalySeverity.CRITICAL,
                List.of(
                        "HikariPool-1 connection acquisition timeout after 30000ms",
                        "Payment-service error rate breached 45% with HTTP 500 DB_TIMEOUT events",
                        "Order checkout latency escalated from 120ms to >3000ms",
                        "Active PostgreSQL connections reached max limit (100/100)"
                ),
                List.of(
                        "14:00 UTC - Flash sale marketing campaign triggered a 10x traffic surge in checkout requests",
                        "14:02 UTC - HikariCP active pool connections on payment-service maxed out at 10",
                        "14:04 UTC - Incoming HTTP threads queued awaiting database connection leases, causing request backlog",
                        "14:06 UTC - Anomaly detector triggered CRITICAL alert for payment-service errorRate (45.2%)",
                        "14:09 UTC - SRE on-call updated maximum pool size to 50 via dynamic configuration and provisioned read-replica",
                        "14:14 UTC - HikariCP queue cleared and error rate normalized to 0.0%"
                ),
                "Missing composite index on payments(order_id, status) forced sequential table scans on payment verification, inflating transaction hold duration from 4ms to 850ms under heavy concurrent load.",
                "Created composite database index 'idx_payments_order_status' and increased HikariCP max-pool-size from 10 to 50 with leakDetectionThreshold set to 5000ms.",
                Set.of("payment-service", "order-service", "postgres"),
                "Implement automated query execution plan analysis in CI pipeline, tune connection pool sizing formulas based on core count, and configure Prometheus HikariCP pending thread alerts.",
                Instant.parse("2026-05-10T14:00:00Z"),
                14
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-002",
                "Inventory Row Locking Contention Draining Database Connections",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                AnomalySeverity.HIGH,
                List.of(
                        "PostgreSQL connection exhaustion on inventory database cluster",
                        "Inventory reservation API returning 'DB_TIMEOUT: Connection pool full'",
                        "Order creation workflow failing with INVENTORY_RESERVATION_FAILED",
                        "Elevated database CPU utilization at 98% with high lock wait times"
                ),
                List.of(
                        "09:15 UTC - Synchronous flash reservation requests began targeting top 3 SKU inventory items",
                        "09:17 UTC - SELECT FOR UPDATE statements blocked concurrent transactions, accumulating idle-in-transaction connections",
                        "09:20 UTC - Connection pool reached 100% capacity with 85 threads waiting in Hikari queue",
                        "09:23 UTC - SRE killed long-running lock-holding transactions using pg_terminate_backend",
                        "09:26 UTC - Deployed hotfix switching to optimistic locking with version column",
                        "09:30 UTC - DB connection pool usage stabilized below 25%"
                ),
                "Pessimistic row locking (SELECT FOR UPDATE) on popular product catalog rows held database connections open during multi-step reservation validation, starving other queries.",
                "Terminated blocking backend sessions and migrated inventory reservation mechanism to optimistic locking with backoff retry strategy.",
                Set.of("inventory-service", "order-service", "postgres"),
                "Establish pessimistic locking linting rules, limit transaction boundaries to strict write operations, and add alerts for pg_stat_activity transactions exceeding 2 seconds.",
                Instant.parse("2026-06-01T09:15:00Z"),
                15
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-003",
                "Unclosed Transaction in Order Cancellation Webhook Depleting DB Pool",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                AnomalySeverity.HIGH,
                List.of(
                        "Gradual creep of active database connections over 4 hours until 100% exhaustion",
                        "Order service health checks failing with Database connection lease timed out",
                        "Incoming payment callback webhooks timing out with HTTP 504",
                        "PostgreSQL reporting high count of connections in 'idle in transaction' state"
                ),
                List.of(
                        "02:00 UTC - Automated batch order cancellation job triggered webhook callbacks",
                        "03:30 UTC - Active connections steadily climbed from 15 to 90 without returning to pool",
                        "05:45 UTC - All 100 pool connections exhausted; new order placements failed immediately",
                        "06:00 UTC - On-call engineer identified leak via Hikari connection leak detection logs",
                        "06:10 UTC - Restarted order-service instances and patched missing @Transactional / try-with-resources",
                        "06:20 UTC - Pool connections returned to healthy baseline of 8 connections"
                ),
                "Exception path during third-party refund API call inside order cancellation flow bypassed transaction closure, leaving database connections in 'idle in transaction' until server restart.",
                "Enclosed external HTTP calls outside database transaction scopes and wrapped EntityManager operations in explicit Spring transactional boundaries.",
                Set.of("order-service", "postgres"),
                "Enforce strict separation between external network calls and database transactions; enable HikariCP leakDetectionThreshold of 2000ms in all environments.",
                Instant.parse("2026-06-18T02:00:00Z"),
                260
        ));

        // =========================================================================
        // CATEGORY 2: DEPLOYMENT REGRESSION (3 Incidents)
        // =========================================================================
        list.add(new HistoricalIncident(
                "HIST-INC-004",
                "Order Service v2.4.1 Jackson Serialization Mismatch Post-Deployment",
                HistoricalIncidentCategory.DEPLOYMENT_REGRESSION,
                AnomalySeverity.CRITICAL,
                List.of(
                        "Immediate 100% failure rate on POST /orders endpoint following rollout of v2.4.1",
                        "Logs flooded with JsonMappingException: Cannot construct instance of OrderRequest",
                        "Payment and inventory services received 0 requests during incident window",
                        "Continuous HTTP 400 Bad Request returned to API gateway and end-users"
                ),
                List.of(
                        "11:00 UTC - Release v2.4.1 deployed to production cluster",
                        "11:02 UTC - Synthetic health checks detected 100% failure rate on order placement",
                        "11:04 UTC - Automated rollback triggered but failed due to manual flag override",
                        "11:07 UTC - SRE on-call executed manual container rollback to v2.4.0",
                        "11:10 UTC - Order creation API successfully validated and restored"
                ),
                "Removal of the default no-arg constructor and missing @JsonProperty annotations on OrderRequest DTO in v2.4.1 broke Jackson JSON deserialization for inbound request payloads.",
                "Rolled back order-service deployment to v2.4.0, restored @JsonProperty annotations and no-arg constructor in v2.4.2 patch release.",
                Set.of("order-service"),
                "Add contract testing between frontend payload specs and backend DTOs in CI/CD pipeline, and enforce automated rollback upon canary error spike.",
                Instant.parse("2026-05-22T11:00:00Z"),
                10
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-005",
                "Inventory Service v1.8.0 Missing Environment Secret Causing CrashLoopBackOff",
                HistoricalIncidentCategory.DEPLOYMENT_REGRESSION,
                AnomalySeverity.CRITICAL,
                List.of(
                        "Inventory-service pods crashing on startup with CrashLoopBackOff",
                        "Order service throwing 503 SERVICE_UNAVAILABLE when calling inventory verification",
                        "Total outage of checkout pipeline across web and mobile channels",
                        "Application log: 'IllegalArgumentException: Could not resolve placeholder INVENTORY_DB_PASSWORD'"
                ),
                List.of(
                        "18:30 UTC - Kubernetes deployment for inventory-service v1.8.0 triggered by CD pipeline",
                        "18:32 UTC - New pods crashed immediately during Spring context initialization",
                        "18:34 UTC - Kubernetes terminated previous healthy revision according to maxSurge/maxUnavailable settings",
                        "18:36 UTC - Complete inventory service availability loss detected by platform monitor",
                        "18:40 UTC - On-call engineer populated missing secret key in Vault and Kubernetes Secrets store",
                        "18:42 UTC - Pods initialized successfully and traffic resumed"
                ),
                "Configuration refactoring in v1.8.0 renamed database password environment variable from DB_PASSWORD to INVENTORY_DB_PASSWORD without updating production Kubernetes Secret manifests.",
                "Applied correct environment secret mapping in Helm release charts and re-deployed inventory-service.",
                Set.of("inventory-service", "order-service"),
                "Implement pre-deployment manifest dry-run and configuration linting against cluster Secret stores in CD pipeline.",
                Instant.parse("2026-06-12T18:30:00Z"),
                12
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-006",
                "Payment Service v3.2.0 Incompatible Date Format Breaking Downstream RestClient",
                HistoricalIncidentCategory.DEPLOYMENT_REGRESSION,
                AnomalySeverity.HIGH,
                List.of(
                        "Order service received DateTimeParseException during payment processing response parsing",
                        "Payment transactions completed in database but marked as failed in Order service",
                        "Spike in customer support complaints regarding double charging",
                        "Order service logs recorded: 'Invalid format: 2026-07-04T12:00:00.123+00:00'"
                ),
                List.of(
                        "15:00 UTC - Payment service v3.2.0 released introducing ISO-8601 offset timestamps",
                        "15:05 UTC - Order service began logging DateTimeParseException on payment confirmation",
                        "15:12 UTC - Anomaly engine detected error rate surge in order-service",
                        "15:20 UTC - SRE identified timestamp format discrepancy between services",
                        "15:28 UTC - Rolled back payment-service to v3.1.9; ran reconciliation script for mismatched orders",
                        "15:45 UTC - All inconsistent order states resolved"
                ),
                "Payment service v3.2.0 changed Jackson ObjectMapper Instant serialization format without updating the shared client SDK, causing RestClient deserializer to crash.",
                "Rolled back payment-service to v3.1.9 and standardized Instant serialization format across common logging and DTO libraries in v3.2.1.",
                Set.of("payment-service", "order-service"),
                "Publish unified SDK with OpenAPI schema validation and backward compatibility checks on all REST payload models.",
                Instant.parse("2026-07-04T15:00:00Z"),
                45
        ));

        // =========================================================================
        // CATEGORY 3: SERVICE UNAVAILABLE (3 Incidents)
        // =========================================================================
        list.add(new HistoricalIncident(
                "HIST-INC-007",
                "Inventory Service JVM OOM Crash Causing Cascading 503 Errors",
                HistoricalIncidentCategory.SERVICE_UNAVAILABLE,
                AnomalySeverity.CRITICAL,
                List.of(
                        "HTTP 503 Service Unavailable returned on all inventory REST endpoints",
                        "JVM container killed by Linux kernel OOM killer (Exit Code 137)",
                        "Order service circuit breaker tripped to OPEN state",
                        "Complete stoppage of product catalog availability checks"
                ),
                List.of(
                        "10:00 UTC - Inventory bulk sync API called with unpaged query of 500,000 SKUs",
                        "10:02 UTC - JVM heap memory consumption climbed to 100% (4GB limit)",
                        "10:03 UTC - Garbage collection pauses exceeded 15 seconds; Kubernetes health check failed",
                        "10:04 UTC - Linux OOM killer terminated inventory-service container",
                        "10:08 UTC - Service restarted automatically; unpaged sync job re-triggered and crashed pod again",
                        "10:12 UTC - SRE disabled the scheduled sync job and restarted inventory-service with increased heap",
                        "10:16 UTC - Service resumed normal operations"
                ),
                "Unbounded pagination in bulk stock sync endpoint loaded the entire product catalog into memory at once, exceeding container memory limits.",
                "Enforced mandatory page size limits (max 500 items) on all bulk endpoints and added JVM memory limits tuning (-XX:MaxRAMPercentage=75).",
                Set.of("inventory-service", "order-service"),
                "Implement strict pagination limits on all query endpoints, add stream-based processing for batch syncs, and alert on JVM heap exceeding 80%.",
                Instant.parse("2026-06-25T10:00:00Z"),
                16
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-008",
                "Kubernetes Node Eviction of Order Service Pods Inducing Traffic Blackhole",
                HistoricalIncidentCategory.SERVICE_UNAVAILABLE,
                AnomalySeverity.CRITICAL,
                List.of(
                        "Order service endpoints returning HTTP 502/503 Bad Gateway via Ingress",
                        "Sudden 90% drop in successfully processed incoming HTTP requests",
                        "Kubernetes event log: 'NodeHasDiskPressure: Evicting Pods'",
                        "Zero healthy endpoints available in order-service Kubernetes Service"
                ),
                List.of(
                        "08:10 UTC - Node disk space filled to 95% due to unrotated docker container logs",
                        "08:12 UTC - Kubelet initiated eviction of non-daemon pods, terminating all 3 order-service replicas simultaneously",
                        "08:14 UTC - Ingress controller received no healthy backend endpoints, responding with 503 Service Unavailable",
                        "08:18 UTC - SRE purged disk logs and provisioned PodDisruptionBudgets (minAvailable: 2)",
                        "08:22 UTC - Pods rescheduled across healthy nodes and traffic returned to normal"
                ),
                "Absence of PodDisruptionBudget (PDB) allowed Kubernetes node eviction to drain all application replicas concurrently when host node encountered disk pressure.",
                "Created PodDisruptionBudget requiring minAvailable: 2 for order-service and configured logrotate on host nodes.",
                Set.of("order-service"),
                "Deploy PodDisruptionBudgets across all microservices and configure disk pressure alerts at 80% threshold.",
                Instant.parse("2026-07-15T08:10:00Z"),
                12
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-009",
                "Payment Service Readiness Probe Flap Causing Ingress Route Oscillation",
                HistoricalIncidentCategory.SERVICE_UNAVAILABLE,
                AnomalySeverity.HIGH,
                List.of(
                        "Intermittent HTTP 503 errors on payment operations (50% failure rate)",
                        "Payment service endpoints flapping between Ready and NotReady states every 30 seconds",
                        "Ingress controller continuously dropping and re-adding payment backends",
                        "Logs indicating Spring Boot Actuator /health endpoint timing out after 1000ms"
                ),
                List.of(
                        "16:20 UTC - Background database index creation caused temporary PostgreSQL query latency spike",
                        "16:22 UTC - Spring Actuator database health check exceeded Kubernetes readiness probe timeout (1s)",
                        "16:23 UTC - Kubernetes removed pods from service endpoints, triggering 503 errors for active callers",
                        "16:28 UTC - SRE increased readiness probe timeoutSeconds from 1 to 5 and failureThreshold to 3",
                        "16:32 UTC - Readiness probe stabilized and endpoint flapping stopped"
                ),
                "Overly aggressive readiness probe configuration (1-second timeout, 1 failure threshold) marked healthy pods as dead during transient DB latency blips.",
                "Adjusted Kubernetes readiness probe parameters: timeoutSeconds=5, periodSeconds=10, failureThreshold=3.",
                Set.of("payment-service", "order-service"),
                "Decouple deep dependency health checks from Kubernetes liveness/readiness probes to avoid false-positive pod restarts.",
                Instant.parse("2026-07-29T16:20:00Z"),
                12
        ));

        // =========================================================================
        // CATEGORY 4: NETWORK LATENCY (3 Incidents)
        // =========================================================================
        list.add(new HistoricalIncident(
                "HIST-INC-010",
                "Cross-Availability-Zone Network Congestion Causing 1500ms Inter-Service Delay",
                HistoricalIncidentCategory.NETWORK_LATENCY,
                AnomalySeverity.HIGH,
                List.of(
                        "Order service end-to-end response time increased from 85ms to 1800ms",
                        "Payment service and Inventory service latency p95 and p99 spiked >2000ms",
                        "Zero application errors observed, but client request timeouts escalated",
                        "TCP round-trip time between AZ-a and AZ-b measured at 450ms"
                ),
                List.of(
                        "13:00 UTC - Cloud provider network interconnect experienced cross-AZ packet degradation",
                        "13:05 UTC - REST calls between order-service (AZ-a) and payment-service (AZ-b) incurred 1500ms latency",
                        "13:10 UTC - Anomaly engine detected latencyAvg deviation (>5x historical baseline)",
                        "13:18 UTC - SRE re-routed inter-service traffic to co-located pods within AZ-a via topology-aware routing",
                        "13:25 UTC - Cross-AZ latency subsided and response times returned to 85ms"
                ),
                "Cloud infrastructure cross-AZ transit link packet drops caused excessive TCP retransmissions for uncompressed REST HTTP/1.1 calls.",
                "Configured Kubernetes topology-aware hints to keep inter-service calls within the same availability zone and enabled HTTP keep-alive connection pooling.",
                Set.of("order-service", "payment-service", "inventory-service"),
                "Implement Kubernetes topology-aware routing and multi-AZ latency telemetry with automated cross-zone failover.",
                Instant.parse("2026-06-08T13:00:00Z"),
                25
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-011",
                "CoreDNS Pod Saturation Inducing 2000ms Intermittent REST Connection Timeouts",
                HistoricalIncidentCategory.NETWORK_LATENCY,
                AnomalySeverity.HIGH,
                List.of(
                        "Sporadic 2000ms latency spikes on order-service downstream HTTP calls",
                        "Intermittent UnknownHostException and SocketTimeoutException during connection establishment",
                        "CoreDNS CPU usage at 100% with DNS query latency >1500ms",
                        "Upstream client timeouts leading to degraded user experience"
                ),
                List.of(
                        "17:00 UTC - High-throughput microservice batch job triggered 50,000 DNS lookups per second",
                        "17:04 UTC - CoreDNS cluster saturated, causing DNS resolution delays up to 2 seconds",
                        "17:08 UTC - Order-service HTTP client threads blocked on DNS resolution when establishing connections",
                        "17:15 UTC - SRE scaled CoreDNS replicas from 2 to 10 and enabled NodeLocal DNSCache",
                        "17:22 UTC - DNS resolution latency dropped to <2ms and REST call latency stabilized"
                ),
                "Every new HTTP connection performed a synchronous DNS lookup due to disabled connection pooling and absent local DNS caching on worker nodes.",
                "Deployed NodeLocal DNSCache daemonset and enabled HTTP client connection reuse across all Spring RestTemplate/RestClient beans.",
                Set.of("order-service", "payment-service", "inventory-service"),
                "Enforce NodeLocal DNSCache in all clusters and require connection pooling with keep-alive on all HTTP clients.",
                Instant.parse("2026-06-30T17:00:00Z"),
                22
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-012",
                "NAT Gateway TCP Port Exhaustion Inducing Intermittent Outbound Request Delays",
                HistoricalIncidentCategory.NETWORK_LATENCY,
                AnomalySeverity.MEDIUM,
                List.of(
                        "Payment gateway outbound integration calls experiencing 3000ms latency spikes",
                        "NAT Gateway metrics showing high ErrorPortAllocation counts",
                        "Payment service connection establishment times exceeding 2000ms",
                        "Customer checkout completion times severely delayed"
                ),
                List.of(
                        "11:30 UTC - Order volume surge opened thousands of short-lived outbound TCP connections to external gateway",
                        "11:35 UTC - NAT Gateway exhausted available source ports (SNAT port exhaustion)",
                        "11:40 UTC - Outbound connections queued in SYN_SENT state awaiting port reclamation",
                        "11:48 UTC - Network engineering provisioned secondary NAT Gateway IPs and enabled TCP TIME_WAIT reuse",
                        "11:55 UTC - Outbound connection latency normalized"
                ),
                "Absence of outbound HTTP connection pooling caused rapid ephemeral port turnover, exhausting NAT Gateway SNAT allocation table.",
                "Enabled Apache HttpClient persistent connection pool with max 200 concurrent connections and allocated multiple egress IPs to NAT Gateway.",
                Set.of("payment-service"),
                "Configure SNAT port utilization alerts at 70% threshold and verify connection pooling on all external integration clients.",
                Instant.parse("2026-07-20T11:30:00Z"),
                25
        ));

        // =========================================================================
        // CATEGORY 5: MEMORY PRESSURE (3 Incidents)
        // =========================================================================
        list.add(new HistoricalIncident(
                "HIST-INC-013",
                "Unbounded In-Memory Event Cache Causing JVM Heap Exhaustion and Full GC Pauses",
                HistoricalIncidentCategory.MEMORY_PRESSURE,
                AnomalySeverity.CRITICAL,
                List.of(
                        "Order service JVM experiencing 8-second Stop-The-World Full GC pauses",
                        "Heap memory utilization pinned at 98% (3.9GB / 4.0GB)",
                        "High latency across all endpoints with intermittent HTTP 504 Gateway Timeouts",
                        "GC log: 'Pause Full (Ergonomics) 8230ms'"
                ),
                List.of(
                        "07:00 UTC - Order creation throughput increased during morning promotion",
                        "07:20 UTC - JVM old generation steadily climbed to 95% without reclaiming space",
                        "07:35 UTC - Concurrent Mark Sweep failed, triggering consecutive 8-second Full GC pauses",
                        "07:42 UTC - Automated alerts notified on-call engineer of extreme p99 latency",
                        "07:48 UTC - Memory dump analysis revealed 2.5GB occupied by ConcurrentHashMap order audit cache",
                        "07:55 UTC - Deployed hotfix replacing unbounded static map with Caffeine bounded cache (maxSize=10000, expireAfterWrite=10m)",
                        "08:05 UTC - Heap usage stabilized at 35% and GC pause times dropped to <15ms"
                ),
                "Static in-memory event map lacked size eviction and TTL policies, retaining millions of completed order event references in JVM heap.",
                "Replaced unbounded ConcurrentHashMap with Caffeine cache configured with size-based eviction and 10-minute time-to-live.",
                Set.of("order-service"),
                "Ban unbounded collections in shared codebases via SonarQube static analysis; monitor JVM Old Gen memory occupancy alerts.",
                Instant.parse("2026-05-18T07:00:00Z"),
                65
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-014",
                "Payment Service ThreadLocal Security Context Leak Leading to Metaspace Exhaustion",
                HistoricalIncidentCategory.MEMORY_PRESSURE,
                AnomalySeverity.HIGH,
                List.of(
                        "Payment service pods crashing with java.lang.OutOfMemoryError: Metaspace",
                        "Gradual memory growth over 72 hours leading to inevitable node termination",
                        "Thread pool worker threads holding stale authentication classloaders",
                        "Periodic micro-outages affecting payment transaction authoring"
                ),
                List.of(
                        "00:00 UTC - Micro-deployments over 3 days generated dynamic runtime proxy classes",
                        "14:00 UTC - ThreadLocal variables retained references to previous classloaders in worker threads",
                        "16:30 UTC - Metaspace reached configured 512MB max limit, throwing OutOfMemoryError",
                        "16:45 UTC - SRE initiated rolling restart of payment-service pods to reclaim metaspace",
                        "17:30 UTC - Engineering team released patch adding ThreadLocal.remove() in HTTP filter finally block",
                        "18:00 UTC - Metaspace stabilized at 120MB"
                ),
                "Custom authentication interceptor failed to invoke ThreadLocal.remove() in error scenarios, preventing garbage collection of web request context and classloaders.",
                "Implemented mandatory cleanup filter utilizing try-finally to execute ThreadLocal.remove() on every request cycle.",
                Set.of("payment-service"),
                "Establish automated leak detection in integration test suites and configure Metaspace monitoring thresholds.",
                Instant.parse("2026-06-20T14:00:00Z"),
                90
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-015",
                "Inventory Service Batch Report Generation Starving JVM Heap Space",
                HistoricalIncidentCategory.MEMORY_PRESSURE,
                AnomalySeverity.HIGH,
                List.of(
                        "Inventory service CPU and memory hitting 99% during daily reconciliation",
                        "Response times for standard stock queries degraded from 15ms to 2400ms",
                        "High frequency of Young GC cycles (every 200ms) causing CPU starvation",
                        "Order service timed out waiting for inventory reservation"
                ),
                List.of(
                        "04:00 UTC - Daily inventory reconciliation report generated in-memory Excel/PDF spreadsheets",
                        "04:05 UTC - 2.8GB allocated to Apache POI spreadsheet object graph in JVM heap",
                        "04:12 UTC - Live transactions starved of heap space, triggering rapid GC thrashing",
                        "04:20 UTC - SRE throttled the batch report job and initiated heap garbage collection",
                        "04:35 UTC - Re-engineered report generator to use SXSSF streaming workbook",
                        "04:50 UTC - Memory footprint during report generation reduced by 90%"
                ),
                "In-memory spreadsheet generator loaded 200,000 inventory items into POI DOM tree simultaneously instead of streaming rows to disk.",
                "Switched from DOM-based spreadsheet generator to streaming SXSSFWorkbook with disk-backed buffer.",
                Set.of("inventory-service", "order-service"),
                "Mandate streaming APIs for all batch reporting and export workflows; isolate reporting tasks to dedicated worker pods.",
                Instant.parse("2026-07-10T04:00:00Z"),
                50
        ));

        // =========================================================================
        // CATEGORY 6: CACHE FAILURE (3 Incidents)
        // =========================================================================
        list.add(new HistoricalIncident(
                "HIST-INC-016",
                "Redis Shared Cluster Eviction Storm Causing Cache Stampede on Inventory Read Path",
                HistoricalIncidentCategory.CACHE_FAILURE,
                AnomalySeverity.CRITICAL,
                List.of(
                        "Redis cache hit ratio plummeted from 98% to 4% within 3 minutes",
                        "PostgreSQL database CPU utilization spiked to 100% due to un-cached query surge",
                        "Inventory lookup latency jumped from 2ms to 1200ms",
                        "Cascading timeouts observed on order creation endpoints"
                ),
                List.of(
                        "15:00 UTC - Bulk promotional item cache keys expired simultaneously due to identical TTL",
                        "15:01 UTC - 10,000 concurrent client requests missed cache and queried PostgreSQL concurrently",
                        "15:03 UTC - PostgreSQL connection pool exhausted, causing inventory service queries to fail",
                        "15:06 UTC - Anomaly detection system alerted on CRITICAL error spike across inventory and order services",
                        "15:10 UTC - SRE enabled Redis single-flight request coalescing (mutex locking) and randomized TTL jitter",
                        "15:18 UTC - Cache hit ratio restored to 97% and database CPU dropped to 18%"
                ),
                "Simultaneous TTL expiration of millions of inventory keys caused a massive thundering herd / cache stampede directly against PostgreSQL.",
                "Applied distributed lock (mutex) on cache misses so only one worker queries the database per key, and introduced ±20% randomized jitter to TTLs.",
                Set.of("inventory-service", "postgres"),
                "Mandate TTL jitter across all caching layers and implement probabilistic early expiration (XFetch algorithm).",
                Instant.parse("2026-05-30T15:00:00Z"),
                18
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-017",
                "Local In-Memory Cache Desynchronization Causing Stale Stock Reads and Overselling",
                HistoricalIncidentCategory.CACHE_FAILURE,
                AnomalySeverity.HIGH,
                List.of(
                        "Inventory service instances returned inconsistent stock levels for identical SKU",
                        "Order service accepted orders for out-of-stock items, resulting in overselling",
                        "Customer cancellation rate surged to 12% for discounted items",
                        "Logs showed mismatch between node local cache and central database truth"
                ),
                List.of(
                        "12:00 UTC - Heavy write traffic updated stock quantities in PostgreSQL without invalidating multi-pod local Guava caches",
                        "12:15 UTC - Node A served stale inventory data (quantity=5) while Node B served (quantity=0)",
                        "12:30 UTC - Warehouse fulfillment rejected 450 orders due to inventory deficit",
                        "12:45 UTC - SRE disabled local caching layer and forced direct database reads",
                        "13:10 UTC - Deployed Redis Pub/Sub cache invalidation topic to synchronize distributed node caches",
                        "13:30 UTC - Data consistency verified across all pods"
                ),
                "Node-local Guava in-memory cache lacked a cluster-wide cache invalidation bus, causing multi-replica inventory services to serve stale stock counts.",
                "Replaced standalone local cache with Redis distributed cache and implemented Redis Pub/Sub invalidation broadcasts.",
                Set.of("inventory-service", "order-service"),
                "Ensure distributed data stores use write-through or event-driven cache invalidation patterns; verify consistency with automated reconciliation.",
                Instant.parse("2026-06-15T12:00:00Z"),
                90
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-018",
                "Payment Token Cache Cold-Start Thundering Herd Overwhelming Token Authority",
                HistoricalIncidentCategory.CACHE_FAILURE,
                AnomalySeverity.HIGH,
                List.of(
                        "Payment service authentication token cache flushed during Redis cluster failover",
                        "Downstream token validation requests overwhelmed internal auth service (504 Gateway Timeout)",
                        "Payment authorizations failed with 'AUTH_TOKEN_UNAVAILABLE'",
                        "Payment success rate dropped from 99.5% to 42%"
                ),
                List.of(
                        "09:00 UTC - Redis master node failover triggered flush of ephemeral payment token cache",
                        "09:02 UTC - Every active payment thread made a synchronous REST call to Auth Service to re-acquire tokens",
                        "09:04 UTC - Auth service thread pool exhausted; response latency rose from 10ms to 5000ms",
                        "09:10 UTC - SRE scaled Auth Service instances by 4x and seeded hot tokens via warmup script",
                        "09:18 UTC - Payment token cache repopulated and payment processing resumed normal throughput"
                ),
                "Absence of cache pre-warming and lack of client-side request deduplication caused thousands of simultaneous token fetches upon cache loss.",
                "Implemented cache warm-up script on service startup and added Resilience4j RequestCoalescer for token acquisition.",
                Set.of("payment-service"),
                "Configure persistent Redis caching (AOF/RDB) and enforce request coalescing for high-cost authorization token lookups.",
                Instant.parse("2026-07-22T09:00:00Z"),
                18
        ));

        // =========================================================================
        // CATEGORY 7: DEPENDENCY TIMEOUT (3 Incidents)
        // =========================================================================
        list.add(new HistoricalIncident(
                "HIST-INC-019",
                "Third-Party Payment Gateway 30-Second HTTP Hang Starving Order Service Threads",
                HistoricalIncidentCategory.DEPENDENCY_TIMEOUT,
                AnomalySeverity.CRITICAL,
                List.of(
                        "Order service Tomcat worker thread pool (200/200 threads) completely exhausted",
                        "Incoming order placement requests rejected with HTTP 503 Server Busy",
                        "Payment service logs filled with: 'Read timed out after 30000ms'",
                        "Total blockage of checkout checkout funnel across all platforms"
                ),
                List.of(
                        "19:00 UTC - External credit card gateway experienced internal database deadlock, hanging open TCP sockets",
                        "19:02 UTC - Payment service client lacked read timeout configuration, waiting 30 seconds per request",
                        "19:05 UTC - Tomcat worker threads backed up across payment-service and order-service",
                        "19:08 UTC - SRE configured Circuit Breaker (Resilience4j) with 3000ms timeout and 50% failure rate trip threshold",
                        "19:15 UTC - Fast-failing circuit breaker shed hung requests and allowed graceful fallback to alternative payment providers",
                        "19:22 UTC - System stabilized with secondary payment gateway active"
                ),
                "Default HTTP client lacked socket read timeouts, allowing external third-party outages to hold Tomcat request threads indefinitely until pool exhaustion.",
                "Enforced strict 3000ms socket read timeout on payment gateway clients and wrapped external calls in a Circuit Breaker with secondary provider fallback.",
                Set.of("payment-service", "order-service"),
                "Mandate explicit connect and read timeouts on all HTTP/gRPC clients and require automated circuit breaking for external dependencies.",
                Instant.parse("2026-05-14T19:00:00Z"),
                22
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-020",
                "Inventory Stock Verification REST Call Exceeding 3s Read Timeout Under Heavy Load",
                HistoricalIncidentCategory.DEPENDENCY_TIMEOUT,
                AnomalySeverity.HIGH,
                List.of(
                        "Order service throwing ResourceAccessException: Read timed out to inventory-service",
                        "Order placement failure rate rose to 35%",
                        "Inventory service CPU at 75% due to un-indexed reservation query",
                        "Customer checkout requests abandoned due to slow response"
                ),
                List.of(
                        "16:00 UTC - Promotion event drove 5x concurrent inventory verification calls",
                        "16:04 UTC - Inventory service response latency drifted above the 3000ms order-service read timeout",
                        "16:08 UTC - Order-service aborted requests while inventory-service continued processing them, creating orphaned reservations",
                        "16:15 UTC - SRE optimized inventory query index and scaled inventory-service instances from 3 to 6",
                        "16:22 UTC - Inventory response time dropped to 45ms and order timeouts ceased"
                ),
                "Inventory service latency exceeded upstream client read timeout threshold under high concurrency due to table lock contention.",
                "Scaled inventory service pods, added database read indexes, and aligned client timeouts with retry policies.",
                Set.of("order-service", "inventory-service"),
                "Implement distributed tracing with OpenTelemetry to track timeout budget consumption across synchronous call chains.",
                Instant.parse("2026-06-28T16:00:00Z"),
                22
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-021",
                "External Tax Calculation Webhook Degradation Blocking Order Completion",
                HistoricalIncidentCategory.DEPENDENCY_TIMEOUT,
                AnomalySeverity.HIGH,
                List.of(
                        "Order checkout API response time degraded from 150ms to 4500ms",
                        "Tax calculation service client throwing SocketTimeoutException",
                        "Orders failing with 'TAX_CALCULATION_UNAVAILABLE'",
                        "Spike in order cancellations at final review step"
                ),
                List.of(
                        "14:30 UTC - Third-party automated tax compliance SaaS experienced severe regional slowdown",
                        "14:35 UTC - Synchronous tax calculation calls inside order creation loop blocked checkout threads",
                        "14:42 UTC - SRE enabled asynchronous tax estimation fallback with post-checkout reconciliation",
                        "14:50 UTC - Checkout latency returned to 150ms; tax differences resolved via background reconciliation worker"
                ),
                "Synchronous coupling of non-critical third-party tax calculation within the primary order transaction path created a single point of failure.",
                "Decoupled tax calculation by implementing cached rate estimation during checkout with asynchronous precision calculation in background.",
                Set.of("order-service"),
                "Identify non-critical path dependencies and enforce asynchronous or fallback-tolerant architectures for third-party integrations.",
                Instant.parse("2026-07-18T14:30:00Z"),
                20
        ));

        // =========================================================================
        // CATEGORY 8: MESSAGE PROCESSING FAILURE (3 Incidents)
        // =========================================================================
        list.add(new HistoricalIncident(
                "HIST-INC-022",
                "Kafka Poison Pill Message in application-logs Halting Consumer Offset Progression",
                HistoricalIncidentCategory.MESSAGE_PROCESSING_FAILURE,
                AnomalySeverity.CRITICAL,
                List.of(
                        "Log-processor Kafka consumer lag grew by 500,000 messages in 15 minutes",
                        "Real-time incident detection and metric aggregation completely stalled",
                        "Logs filled with: 'SerializationException: Unexpected token at offset 0'",
                        "Consumer repeatedly crashing and re-fetching the same un-parseable offset"
                ),
                List.of(
                        "21:00 UTC - Malformed non-JSON binary log payload published to 'application-logs' topic by misconfigured test agent",
                        "21:02 UTC - LogProcessor consumer encountered DeserializationException and failed batch processing",
                        "21:05 UTC - Kafka consumer re-polled the same poison pill message infinitely without committing offset",
                        "21:10 UTC - On-call engineer configured ErrorHandlingDeserializer and Dead Letter Queue (DLQ) topic",
                        "21:15 UTC - Poison pill routed to DLQ topic; consumer lag rapidly recovered to 0",
                        "21:20 UTC - Platform log processing and anomaly metrics caught up to real-time"
                ),
                "Absence of Dead Letter Queue (DLQ) and ErrorHandlingDeserializer caused single invalid message to block all subsequent partition records.",
                "Configured Spring Kafka ErrorHandlingDeserializer with DeadLetterPublishingRecoverer and published poison pills to 'application-logs.DLT'.",
                Set.of("log-processor", "kafka"),
                "Ensure all Kafka consumers configure ErrorHandlingDeserializer and dead-letter topics with automated schema validation.",
                Instant.parse("2026-05-05T21:00:00Z"),
                20
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-023",
                "Kafka Consumer Rebalance Storm Triggered by Long-Running Payment Processing",
                HistoricalIncidentCategory.MESSAGE_PROCESSING_FAILURE,
                AnomalySeverity.HIGH,
                List.of(
                        "Payment event consumer group constantly rebalancing (RebalanceInProgressException)",
                        "Zero payment events processed for 25 minutes; consumer lag reached 120,000 records",
                        "Duplicate payment notification emails sent to end-users",
                        "Kafka broker logs: 'Member failed to send heartbeat within max.poll.interval.ms'"
                ),
                List.of(
                        "10:00 UTC - Batch payment verification job processed slow external banking queries (12s per record)",
                        "10:08 UTC - Total batch processing time exceeded max.poll.interval.ms (300,000ms)",
                        "10:10 UTC - Kafka coordinator declared consumer dead and triggered group rebalance",
                        "10:12 UTC - Next consumer picked up the uncommitted batch, timed out, and triggered another rebalance",
                        "10:20 UTC - SRE increased max.poll.interval.ms to 600,000ms and reduced max.poll.records from 500 to 50",
                        "10:28 UTC - Rebalance loop halted and consumer group processed backlog cleanly"
                ),
                "Large batch sizes (max.poll.records=500) combined with synchronous downstream API latency exceeded Kafka consumer poll timeout, causing endless rebalances.",
                "Decreased max.poll.records to 50, tuned max.poll.interval.ms, and converted payment verification to asynchronous thread pool executor.",
                Set.of("payment-service", "kafka"),
                "Tune max.poll.records according to worst-case p99 processing time and monitor Kafka consumer group rebalance frequency.",
                Instant.parse("2026-06-22T10:00:00Z"),
                28
        ));

        list.add(new HistoricalIncident(
                "HIST-INC-024",
                "Deployment Event Consumer Deserialization Failure Due to Unknown Enum Value",
                HistoricalIncidentCategory.MESSAGE_PROCESSING_FAILURE,
                AnomalySeverity.MEDIUM,
                List.of(
                        "Deployment events pipeline failed to record DEPLOYMENT_CANARY_PROMOTED events",
                        "Deployment history in incident timeline missing version transition markers",
                        "Log processor logged: 'Cannot deserialize value of type DeploymentStatus from String'",
                        "Consumer partition paused due to unhandled deserialization exception"
                ),
                List.of(
                        "13:00 UTC - CI/CD pipeline introduced new deployment event status 'CANARY_PROMOTED'",
                        "13:05 UTC - Log processor deployment event consumer failed to deserialize new enum constant",
                        "13:12 UTC - Incident engine stopped receiving deployment correlation context",
                        "13:20 UTC - SRE patched DeploymentStatus enum with @JsonEnumDefaultValue fallback to UNKNOWN",
                        "13:30 UTC - Log processor re-processed deployment event stream and recorded canary milestones"
                ),
                "Strict enum deserialization without default fallback caused parser failure when upstream service introduced a new event type.",
                "Added READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE Jackson configuration and updated shared deployment event model.",
                Set.of("log-processor", "kafka"),
                "Configure Jackson deserializers to handle unknown enum values gracefully and use Schema Registry for event evolution.",
                Instant.parse("2026-07-25T13:00:00Z"),
                30
        ));

        return list;
    }
}
