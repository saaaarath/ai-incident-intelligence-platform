package com.aiincident.logprocessor.historical;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Curated canonical operational post-mortem dataset associated with historical incidents.
 */
public final class PostmortemDataset {

    private PostmortemDataset() {
    }

    public static List<Postmortem> getCanonicalPostmortems() {
        List<Postmortem> list = new ArrayList<>();

        // PM 1: HIST-INC-001
        list.add(new Postmortem(
                "PM-HIST-INC-001",
                "HIST-INC-001",
                "Payment Service HikariCP Connection Pool Saturation During Flash Sale Postmortem",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                AnomalySeverity.CRITICAL,
                "Principal SRE & Payment Platform Tech Lead",
                "During a planned flash sale marketing promotion, payment-service experienced a critical database connection pool exhaustion resulting in 45% of customer checkout requests failing for 14 minutes.",
                "1,450 customer orders failed due to database timeouts over a 14-minute window. Estimated revenue impact was temporary as affected users successfully retried following pool expansion.",
                "A newly introduced payment ledger verification query lacked a composite index on (order_id, status), forcing sequential table scans that inflated database transaction hold duration from 4ms to 850ms under heavy concurrent traffic.",
                "Detected automatically by Z-Score and baseline anomaly detection when payment-service error rate breached 5% threshold (Z-Score = 7.4). MTTD was 4 minutes; MTTR was 10 minutes following pool configuration update.",
                List.of(
                        "Add composite index 'idx_payments_order_status' to production PostgreSQL database [COMPLETED]",
                        "Increase default HikariCP connection pool size from 10 to 50 [COMPLETED]",
                        "Implement automated EXPLAIN query plan analysis in CI pipeline [PENDING - Ticket #PLAT-412]",
                        "Configure Prometheus alerts for HikariCP pending thread count > 5 for 1 minute [COMPLETED]"
                ),
                List.of(
                        "What went well: Automated anomaly detection alerted on-call before customer support escalations.",
                        "What went wrong: Synthetic load testing prior to flash sale did not replicate concurrent ledger verification query volume.",
                        "Where we got lucky: Read-replica was provisioned and idle, enabling quick offloading."
                ),
                null,
                Instant.parse("2026-05-11T10:00:00Z")
        ));

        // PM 2: HIST-INC-002
        list.add(new Postmortem(
                "PM-HIST-INC-002",
                "HIST-INC-002",
                "Inventory Row Locking Contention Draining Database Connections Postmortem",
                HistoricalIncidentCategory.DATABASE_CONNECTION_EXHAUSTION,
                AnomalySeverity.HIGH,
                "Senior SRE & Inventory Team Lead",
                "Pessimistic row locking on popular inventory items caused severe lock contention, exhausting database connection pool for 15 minutes during top SKU promotions.",
                "2,100 stock check and reservation calls failed with DB_TIMEOUT. Order placement funnel degraded by 30% during the incident window.",
                "Pessimistic row locking (SELECT ... FOR UPDATE) held database connections open across multi-step reservation validations, causing incoming transactions to accumulate in HikariCP acquisition queue.",
                "Detected by inventory-service latency anomaly detector when p99 latency breached 2500ms. MTTD was 3 minutes; MTTR was 12 minutes.",
                List.of(
                        "Migrate inventory reservation from pessimistic locking to optimistic locking with version column [COMPLETED]",
                        "Add statement_timeout = 3000ms on all inventory database connections [COMPLETED]",
                        "Add automated lock wait time monitoring in Grafana [COMPLETED]"
                ),
                List.of(
                        "What went well: SRE quickly identified blocking PID sessions using pg_stat_activity.",
                        "What went wrong: Pessimistic locking pattern was not flagged during code review.",
                        "Where we got lucky: No duplicate reservations or inventory overselling occurred during contention."
                ),
                null,
                Instant.parse("2026-06-02T11:00:00Z")
        ));

        // PM 3: HIST-INC-004
        list.add(new Postmortem(
                "PM-HIST-INC-004",
                "HIST-INC-004",
                "Order Service v2.4.1 Jackson Serialization Mismatch Postmortem",
                HistoricalIncidentCategory.DEPLOYMENT_REGRESSION,
                AnomalySeverity.CRITICAL,
                "Release Manager & Core Backend Architect",
                "Deployment of order-service v2.4.1 introduced a breaking Jackson serialization regression, causing 100% of order placement requests to fail with HTTP 400 for 10 minutes until rollback.",
                "850 order placements rejected during the 10-minute deployment window. Zero data corruption occurred.",
                "Removal of the default no-arg constructor and missing @JsonProperty annotations on OrderRequest DTO prevented Jackson from deserializing incoming JSON request bodies.",
                "Automated post-deployment canary health checks detected a 100% HTTP 400 error rate within 2 minutes of rollout. SRE executed manual rollback to v2.4.0 in 8 minutes.",
                List.of(
                        "Enforce strict contract testing for all API request/response DTOs in CI [COMPLETED]",
                        "Configure automated rollback in ArgoCD canary deployment pipeline on error rate > 2% [COMPLETED]",
                        "Publish patch release v2.4.2 with restored no-arg constructor and JSON annotations [COMPLETED]"
                ),
                List.of(
                        "What went well: Rollback procedure completed cleanly in under 3 minutes once initiated.",
                        "What went wrong: Local unit tests used builder pattern directly, bypassing Jackson deserialization.",
                        "Where we got lucky: Deployment occurred during off-peak morning traffic."
                ),
                null,
                Instant.parse("2026-05-23T09:00:00Z")
        ));

        // PM 4: HIST-INC-007
        list.add(new Postmortem(
                "PM-HIST-INC-007",
                "HIST-INC-007",
                "Inventory Service JVM OOM Panic & Cascading 503 Outage Postmortem",
                HistoricalIncidentCategory.SERVICE_UNAVAILABLE,
                AnomalySeverity.CRITICAL,
                "Platform Operations Lead & SRE",
                "An unpaged bulk inventory catalog sync job consumed all available JVM heap space, triggering OutOfMemoryError and kernel container termination (Exit code 137) across all inventory replicas.",
                "Complete outage of inventory verification REST endpoints for 16 minutes. Order service responded with 503 Service Unavailable to end-users.",
                "Bulk sync endpoint executed an unbounded JPA query loading 500,000 product records into memory simultaneously, exceeding the 4GB container memory limit.",
                "Triggered Kubernetes OOMKilled events and liveness probe failures. Anomaly detection engine logged CRITICAL SERVICE_UNAVAILABLE event within 60 seconds.",
                List.of(
                        "Enforce mandatory pagination (max 500 items per page) on all bulk queries [COMPLETED]",
                        "Configure container memory limits to 6GB with -XX:MaxRAMPercentage=75 [COMPLETED]",
                        "Isolate batch catalog synchronization jobs to dedicated asynchronous worker pods [COMPLETED]"
                ),
                List.of(
                        "What went well: Kubernetes container restart attempted self-healing immediately.",
                        "What went wrong: Scheduled cron job immediately re-triggered upon restart, inducing a crash loop.",
                        "Where we got lucky: Circuit breaker on Order Service prevented cascading thread pool exhaustion in upstream gateway."
                ),
                null,
                Instant.parse("2026-06-26T14:00:00Z")
        ));

        // PM 5: HIST-INC-010
        list.add(new Postmortem(
                "PM-HIST-INC-010",
                "HIST-INC-010",
                "Cross-Availability-Zone Interconnect Congestion Latency Spike Postmortem",
                HistoricalIncidentCategory.NETWORK_LATENCY,
                AnomalySeverity.HIGH,
                "Cloud Infrastructure Engineer & SRE",
                "Cloud provider transit link degradation between AZ-a and AZ-b increased inter-service REST call latency from 85ms to 1800ms, causing widespread client timeouts for 25 minutes.",
                "Customer checkout completion times surged to > 4 seconds. Approximately 350 client-side timeouts recorded.",
                "Uncompressed HTTP/1.1 REST calls traversing cross-AZ boundary suffered heavy TCP packet retransmissions during cloud provider network switch degradation.",
                "Detected by operational metrics aggregation layer when latencyAvg for order-service breached 5x historical baseline mean.",
                List.of(
                        "Configure Kubernetes topology-aware hints to prioritize intra-AZ service routing [COMPLETED]",
                        "Enable HTTP keep-alive connection pooling across all Spring RestTemplate/RestClient beans [COMPLETED]",
                        "Implement multi-AZ latency telemetry alerting with automated traffic re-weighting [COMPLETED]"
                ),
                List.of(
                        "What went well: Anomaly detection isolated cross-AZ latency before errors cascaded into failures.",
                        "What went wrong: Inter-service calls were distributed randomly across zones without locality awareness.",
                        "Where we got lucky: No data was lost or corrupted during transit."
                ),
                null,
                Instant.parse("2026-06-09T10:00:00Z")
        ));

        // PM 6: HIST-INC-013
        list.add(new Postmortem(
                "PM-HIST-INC-013",
                "HIST-INC-013",
                "Order Service Unbounded In-Memory Cache JVM Heap Exhaustion Postmortem",
                HistoricalIncidentCategory.MEMORY_PRESSURE,
                AnomalySeverity.CRITICAL,
                "Senior SRE & Performance Engineer",
                "A static in-memory audit cache in Order Service grew unbounded over 14 hours, causing 8-second Stop-The-World Full GC pauses and HTTP 504 Gateway Timeouts.",
                "High latency and intermittent gateway timeouts affected 12,000 order requests over a 65-minute degradation window.",
                "A static ConcurrentHashMap intended for local audit deduplication lacked eviction policies, retaining millions of completed order event references in JVM heap.",
                "Detected when Prometheus JVM metrics recorded Old Gen memory utilization > 95% and consecutive Full GC pauses > 5000ms.",
                List.of(
                        "Replace unbounded ConcurrentHashMap with Caffeine bounded cache (maxSize=10000, expireAfterWrite=10m) [COMPLETED]",
                        "Add SonarQube static analysis rule banning unbounded collections in production code [COMPLETED]",
                        "Configure Grafana alerts on JVM Old Gen memory > 80% for 3 minutes [COMPLETED]"
                ),
                List.of(
                        "What went well: Heap dump analysis using Eclipse Memory Analyzer (MAT) identified the leaking map in under 10 minutes.",
                        "What went wrong: Memory leak was not detected in staging due to short test duration.",
                        "Where we got lucky: Service remained partially responsive between GC cycles, avoiding complete hard crash."
                ),
                null,
                Instant.parse("2026-05-19T12:00:00Z")
        ));

        // PM 7: HIST-INC-016
        list.add(new Postmortem(
                "PM-HIST-INC-016",
                "HIST-INC-016",
                "Redis Cache Stampede & Database Saturation Outage Postmortem",
                HistoricalIncidentCategory.CACHE_FAILURE,
                AnomalySeverity.CRITICAL,
                "Database Administrator & Lead SRE",
                "Simultaneous expiration of millions of inventory item cache keys caused a massive cache stampede, driving PostgreSQL CPU to 100% and timing out order checkouts for 18 minutes.",
                "7,800 inventory lookup requests failed or timed out. Database connection pool was exhausted, degrading all dependent checkout flows.",
                "All promotional product inventory cache keys were seeded with an identical 24-hour TTL, causing millions of keys to expire simultaneously at 15:00 UTC and flooding PostgreSQL with concurrent read queries.",
                "Detected by database connection pool anomaly detector and cache hit ratio drop from 98% to 4% within 3 minutes.",
                List.of(
                        "Implement single-flight request coalescing (mutex locking) on cache misses [COMPLETED]",
                        "Add randomized TTL jitter (±20%) to all cached key expiration policies [COMPLETED]",
                        "Implement probabilistic early expiration (XFetch algorithm) on high-traffic keys [COMPLETED]"
                ),
                List.of(
                        "What went well: SRE applied Redis single-flight coalescing quickly, halting the DB query flood.",
                        "What went wrong: Cache seeding scripts used static expiration times without jitter.",
                        "Where we got lucky: Database primary instance did not crash or corrupt transaction logs."
                ),
                null,
                Instant.parse("2026-05-31T11:00:00Z")
        ));

        // PM 8: HIST-INC-019
        list.add(new Postmortem(
                "PM-HIST-INC-019",
                "HIST-INC-019",
                "Third-Party Payment Gateway HTTP Socket Hang Postmortem",
                HistoricalIncidentCategory.DEPENDENCY_TIMEOUT,
                AnomalySeverity.CRITICAL,
                "Principal Architect & Payment Platform SRE",
                "An external payment gateway database deadlock caused outgoing HTTP sockets to hang indefinitely, consuming all 200 Tomcat worker threads in Order Service and halting checkout.",
                "Total checkout outage for 22 minutes affecting 3,400 checkout attempts.",
                "Default HTTP client lacked socket read timeouts, allowing hung external TCP connections to block application worker threads indefinitely until pool exhaustion.",
                "Detected when Order Service thread pool utilization hit 100% and incoming requests began receiving 503 Server Busy.",
                List.of(
                        "Configure strict 3000ms socket read timeout and 2000ms connect timeout on all HTTP clients [COMPLETED]",
                        "Implement Resilience4j Circuit Breaker with automatic failover to secondary payment provider [COMPLETED]",
                        "Add synthetic monitoring on third-party payment gateway endpoints [COMPLETED]"
                ),
                List.of(
                        "What went well: Secondary payment provider integration was available and ready to accept traffic.",
                        "What went wrong: Absence of client timeouts allowed external outage to starve internal services.",
                        "Where we got lucky: Gateway vendor resolved their deadlock quickly, assisting in verification."
                ),
                null,
                Instant.parse("2026-05-15T15:00:00Z")
        ));

        // PM 9: HIST-INC-022
        list.add(new Postmortem(
                "PM-HIST-INC-022",
                "HIST-INC-022",
                "Kafka Poison Pill Payload in application-logs Pipeline Postmortem",
                HistoricalIncidentCategory.MESSAGE_PROCESSING_FAILURE,
                AnomalySeverity.CRITICAL,
                "Messaging Platform Lead & SRE",
                "A malformed binary log payload published to the application-logs topic caused log-processor consumers to crash repeatedly on DeserializationException, halting real-time telemetry processing for 20 minutes.",
                "Consumer lag reached 500,000 un-processed messages. Real-time incident detection and operational metrics aggregation were stalled for 20 minutes.",
                "Kafka consumer lacked ErrorHandlingDeserializer and Dead Letter Queue (DLQ), causing it to poll the identical un-parseable record repeatedly without committing offset progression.",
                "Detected by consumer lag monitoring alert when lag exceeded 50,000 records threshold.",
                List.of(
                        "Configure Spring Kafka ErrorHandlingDeserializer with DeadLetterPublishingRecoverer routing to application-logs.DLT [COMPLETED]",
                        "Implement strict JSON schema validation prior to publishing log events to Kafka [COMPLETED]",
                        "Add automated Dead Letter Topic alerting and dead-letter replay tooling [COMPLETED]"
                ),
                List.of(
                        "What went well: No log events were permanently lost; all messages were processed once DLQ was enabled.",
                        "What went wrong: A single bad event halted processing for all subsequent valid events on the partition.",
                        "Where we got lucky: Broker storage capacity was sufficient to buffer the entire backlog during the outage."
                ),
                null,
                Instant.parse("2026-05-06T10:00:00Z")
        ));

        return list;
    }
}
