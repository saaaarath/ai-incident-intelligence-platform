# AI Incident Intelligence Platform

The AI Incident Intelligence Platform is an incremental monorepo for exploring operational intelligence across business services. The platform will eventually collect service signals, analyze incidents, and present useful operational context to engineering teams.

## Architecture

- `services/` contains independently deployable Java and Spring Boot services for orders, payments, and inventory.
- `common/operational-logging/` contains the shared JSON operational event model, tracing, failure injection, and asynchronous Kafka log publisher used by all services.
- `incident-engine/log-processor` is the Spring Boot service that consumes operational logs from Kafka, validates them, and persists them into PostgreSQL.
- `ai-engine/` is reserved for future AI and retrieval workflows.
- `dashboard/` is reserved for a future React + Vite operator interface.
- `infrastructure/` contains Docker Compose, Kafka (KRaft), PostgreSQL, and deployment assets.
- `test-scenarios/` is reserved for future end-to-end and failure scenarios.
- `docs/` contains project and phase documentation.
- PostgreSQL is the persistence technology for all services and is configured through environment variables.

The root Maven project is an aggregator for the current Java services and incident engine. Each service owns its own Spring Boot application and build configuration so later assignments can evolve services independently.

## Current Status

Order Service, Payment Service, and Inventory Service are independently functional. Order creation can synchronously call Payment Service and then Inventory Service over REST, with configurable URLs, timeouts, and failure handling. They provide JPA-backed APIs with PostgreSQL configuration and isolated H2 integration tests. Inventory reservations use transactional row locking to prevent stock from being oversold. Apache Kafka (KRaft mode) is configured as local infrastructure with standard topics (`application-logs`, `service-events`, `deployment-events`). Services stream operational logs asynchronously to Kafka, and the `log-processor` consumes, validates, and persists them into the `application_logs` table in PostgreSQL. The pipeline also supports deployment lifecycle events (`DEPLOYMENT_STARTED`, `DEPLOYMENT_COMPLETED`) streamed via the `deployment-events` Kafka topic and stored in PostgreSQL `deployment_events` table alongside application events. There is no AI, RAG, anomaly detection, incident management, or dashboard functionality yet.

For the integrated Order flow, configure `PAYMENT_SERVICE_URL` and `INVENTORY_SERVICE_URL` in addition to the Order Service database variables. Downstream timeout defaults are 2 seconds to connect and 3 seconds to read.

The Compose stack publishes Order Service on host port `18080` (set `ORDER_SERVICE_PORT` to change), Log Processor on host port `18084` (set `LOG_PROCESSOR_PORT` to change), and Kafka on host port `29092` by default (set `KAFKA_PORT` to change).

## Operational logging

Business outcomes and failures are written through the common structured logger as one JSON object per log line. Each event contains `eventId`, `timestamp`, `service`, `level`, `eventType`, `traceId`, `message`, and `metadata`. Current event types include `ORDER_CREATED`, `PAYMENT_CREATED`, `PAYMENT_FAILED`, `INVENTORY_RESERVED`, `INVENTORY_RESERVATION_FAILED`, `DB_TIMEOUT`, and `SERVICE_UNAVAILABLE`. Logs are written to SLF4J and simultaneously streamed to the `application-logs` Kafka topic.

## Request Correlation

Request correlation is maintained across the synchronous production flow:
- Every incoming HTTP request is assigned a `traceId`. If the client provides an `X-Trace-Id` header, that trace ID is adopted; otherwise a new UUID is generated.
- The `traceId` is bound to the logging MDC context for the duration of the request and returned to the client in the `X-Trace-Id` response header.
- Downstream HTTP REST clients automatically propagate the active `traceId` via the `X-Trace-Id` header across service boundaries (`Order Service` -> `Payment Service` -> `Inventory Service`).
- All structured operational log events generated during a request include the same `traceId`, allowing an entire business flow across services to be correlated with a single identifier.

## Failure Injection

A controlled failure-injection mechanism is available for demo and chaos simulation:
- **Supported Modes**: `DB_FAILURE`, `LATENCY`, `SERVICE_UNAVAILABLE`, `ERROR_SPIKE`.
- **Internal Control API**:
  - `GET /internal/failures`: Inspect current failure status.
  - `POST /internal/failures`: Enable failure mode (e.g. `{"type": "DB_FAILURE"}`, `{"type": "LATENCY", "latencyMs": 3000}`).
  - `DELETE /internal/failures`: Disable failure injection, returning service to normal behavior immediately.
- **Security & Isolation**: Control endpoints reside under `/internal/failures`, bypassed by failure injection filters, and configurable with `failure.injection.enabled` and optional `X-Internal-Token` validation.
## Operational Metrics Aggregation

An aggregation layer calculates operational metrics over persisted operational events stored in the `application_logs` table:
- **Per Service & Time Window**: Calculates total requests/events, error count, error rate (ratio of error events to total events), and latency percentiles/averages (`min`, `max`, `avg`, `p50`, `p95`, `p99`) when available in event metadata.
- **Configurable Fixed Windows**: Supports time-bucketed aggregation with configurable fixed windows (default: 1 minute, configurable via `metrics.aggregation.default-window-minutes`).
- **REST Endpoints**:
  - `GET /api/metrics`: Retrieve time-windowed metrics for a service (or all services) across a time range (`from`, `to`, `service`, `windowMinutes`, `windowSeconds`).
  - `GET /api/metrics/summary`: Retrieve single-window metric summary across an entire range.
## Baseline-Based Anomaly Detection

A statistical and threshold-based anomaly detection engine monitors service health against historical baselines:
- **Baseline Metrics**: Computes baseline mean ($\mu$) and baseline variability ($\sigma$, standard deviation) over historical time windows per service and metric (`errorRate`, `latencyAvg`).
- **Deviation & Threshold Detection**:
  - Compares the current time window against the historical baseline.
  - Triggers an anomaly event when current metrics breach statistical sigma thresholds (e.g. $\ge 3\sigma$) or configurable absolute thresholds (e.g. error rate $\ge 5\%$, latency spikes).
  - Assigns severity levels (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) based on the magnitude of the deviation.
  - Automatically avoids false alerts during normal service behavior within expected baseline variability.
- **Anomaly Event Schema**: Every generated anomaly event records `metric`, `service`, `currentValue`, `baselineMean`, `baselineVariability`, `threshold`, `detectedAt`, `severity`, `windowStart`, `windowEnd`, and `message`.
- **REST Endpoints**:
  - `GET /api/anomalies`: Query detected anomalies filtered by `service`, `metric`, `severity`, `from`, `to`.
  - `POST /api/anomalies/detect`: Trigger anomaly detection over specified time windows (`strategy=THRESHOLD`, `strategy=ZSCORE`, or `strategy=ALL`) and persist detected anomaly events.

## Z-Score Anomaly Detection

A dedicated statistical detector evaluates metric anomalies using standard Z-score analysis:
- **Formula**: $z = \frac{x - \mu}{\sigma}$ where $\mu$ is historical baseline mean and $\sigma$ is baseline standard deviation.
- **Zero Standard Deviation Safety**: Safely handles zero standard deviation (e.g. constant 0% error rate or constant latency) without division-by-zero, evaluating meaningful deviations above the noise floor.
- **Configurable Thresholds**: Configurable via `anomaly.zscore.threshold` (default: 3.0), `anomaly.zscore.min-samples` (default: 3), and `anomaly.zscore.zero-sigma-min-diff` (default: 0.05).
- **Seamless Incident Integration**: Z-Score anomalies produce the standard `AnomalyEvent` format and automatically feed into the incident creation and correlation engine.

## Incident Management

An incident correlation and management engine converts detected anomalies into tracked incidents:
- **Incident Model**: Stored in `incidents` table with fields `id`, `title`, `severity` (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), `status` (`OPEN`, `INVESTIGATING`, `RESOLVED`, `CLOSED`), `primaryService`, `startedAt`, `detectedAt`, and `resolvedAt`.
- **Automated Incident Creation**: When an anomaly crosses the incident severity threshold (default: `MEDIUM`), an incident is automatically created in `OPEN` status.
- **Duplicate Incident Prevention**: Consecutive or related anomalies for the same service within an active failure window correlate to the active incident instead of generating duplicate records. If incoming anomalies exhibit higher severity, the active incident's severity is dynamically upgraded.
- **Lifecycle Transitions**: Full state machine (`OPEN` -> `INVESTIGATING` -> `RESOLVED` -> `CLOSED`). Moving to `RESOLVED` or `CLOSED` automatically records `resolvedAt` timestamp.
- **REST Endpoints**:
  - `GET /incidents` (or `GET /api/incidents`): Query incidents with filtering by `status`, `severity`, `service`, and time range (`from`, `to`).
  - `GET /incidents/{id}`: Retrieve a specific incident by ID (returns 404 if not found).
  - `POST /incidents/{id}/acknowledge`: Transition incident from `OPEN` to `INVESTIGATING`.
  - `POST /incidents/{id}/resolve`: Transition incident to `RESOLVED` and record resolution timestamp.
  - `POST /incidents/{id}/close`: Transition incident to `CLOSED`.
  - `PATCH /incidents/{id}/status` (or `PATCH /api/incidents/{id}/status`): Update lifecycle status directly.
  - `POST /incidents`: Manually register an incident.

## Incident Time-Window & Cascading Failure Correlation

A deterministic, rule-based correlation engine groups cascading operational failure events across services into unified incidents:
- **Multi-Dimensional Correlation**:
  - **Time Proximity**: Groups events occurring within a configurable sliding correlation time window (default: 60 seconds via `incident.correlation-window-seconds`).
  - **Service Topology**: Recognizes caller-callee and dependency relationships via `ServiceDependencyGraph` (`order-service` -> `payment-service` / `inventory-service` -> `database`), grouping downstream cascade failures into the root originating incident.
  - **Event Type Taxonomy**: Evaluates failure indicators (`DB_TIMEOUT`, `POOL_EXHAUSTED`, `PAYMENT_FAILED`, `ORDER_TIMEOUT`, `SERVICE_UNAVAILABLE`) and prioritizes root causes.
  - **Active Incident State**: Correlates new evidence into `OPEN` or `INVESTIGATING` incidents, tracking all `affectedServices`, updating `lastEventAt`, and dynamically upgrading severity when higher-severity events occur.
- **Evidence Chain**: Correlated events are persisted in `incident_evidence` table linked to the parent incident.
- **REST Endpoints**:
  - `GET /incidents/{id}/evidence`: Retrieve the complete chronological evidence chain for an incident.
  - `POST /api/incidents/correlate`: Trigger correlation across stored events for a given time window.

## Error Fingerprinting

A normalization and fingerprinting engine groups equivalent operational failures across dynamic IDs and timestamps:
- **Normalization Strategy**:
  - Replaces variable tokens (UUIDs, ISO/RFC timestamps, times, dates, IP addresses, ports, hex hashes, named IDs like `order_id=123`, and numeric latencies/durations) with canonical placeholders (`<UUID>`, `<TIMESTAMP>`, `<TIME>`, `<DATE>`, `<IP>`, `<HEX>`, `<ID>`, `<NUM>`).
  - Produces a canonical lowercase template pattern (`service:eventType:normalizedMessage`).
- **Deterministic Fingerprint**: Generates a standard SHA-256 hash identifying the underlying error pattern, ensuring errors with varying order IDs, timestamps, or execution times map to the identical fingerprint.
- **Incident & Evidence Tracking**: Both `incidents` and `incident_evidence` record the associated `fingerprint`.
- **REST Endpoints**:
  - `POST /api/fingerprints/generate` (or `POST /api/fingerprints/normalize`): Generate normalized error pattern and fingerprint hash for given parameters.
  - `GET /api/fingerprints`: Query aggregated fingerprint summaries and occurrence counts over a time window.
  - `GET /api/fingerprints/groups`: Retrieve log events grouped by error fingerprint.
  - `GET /api/incidents?fingerprint=...`: Filter tracked incidents by error fingerprint hash.

## Service Dependency Graph

A maintainable PostgreSQL-backed dependency graph models microservice topologies without Neo4j:
- **Initial Topologies**:
  - `Order Service` $\rightarrow$ `Payment Service`
  - `Payment Service` $\rightarrow$ `Inventory Service`
  - `Payment Service` $\rightarrow$ `PostgreSQL`
  - `Order Service` $\rightarrow$ `Inventory Service`
  - `Inventory Service` $\rightarrow$ `PostgreSQL`
- **Database Model**: Stored in `service_dependencies` table with fields `id`, `sourceService`, `targetService`, `dependencyType` (`HTTP_REST`, `DATABASE`, `MESSAGE_QUEUE`, `GRPC`), `criticality`, and `description`.
- **Graph & Topological Traversal**: Computes direct downstream dependencies, upstream callers, and full transitive reachability.
- **REST Endpoints**:
  - `GET /api/dependencies`: Retrieve all configured service dependencies.
  - `GET /api/dependencies/{service}`: Retrieve full topology (upstream, downstream, all related) for a service.
  - `GET /api/dependencies/{service}/downstream`: List downstream services called by this service.
  - `GET /api/dependencies/{service}/upstream`: List upstream callers that depend on this service.
  - `POST /api/dependencies`: Register or update a service dependency.
  - `DELETE /api/dependencies`: Remove a dependency link.

## Primary Failure vs Downstream Symptoms (Deterministic RCA)

A multi-factor scoring engine distinguishes originating primary failures from downstream cascading symptoms without an LLM:
- **Deterministic 4-Factor Scoring Model (0 - 100 points)**:
  - **Temporal Precedence (0 - 40 pts)**: Earlier initial anomalies receive maximum points; subsequent failures decay proportionally over the cascade duration.
  - **Dependency Topology Position (0 - 30 pts)**: Leaf/sink dependencies (e.g. `PostgreSQL`, `Database`) and depended-upon callee services receive caller-support bonuses; upstream callers depending on already failing downstream services receive symptom penalties.
  - **Error Severity (0 - 20 pts)**: Weighted by maximum observed event severity (`CRITICAL`=20, `HIGH`=15, `MEDIUM`=10, `LOW`=5).
  - **Frequency & Error Concentration (0 - 10 pts)**: Error burst density and event volume relative to the overall incident window.
- **Root Cause & Symptom Categorization**: Automatically ranks candidate services, identifies the highest-scoring candidate as the `primary` root cause, assigns a confidence level (`HIGH`, `MEDIUM`, `LOW`), tags downstream affected services as `symptoms`, and generates deterministic reasoning.
- **REST Endpoints**:
  - `GET /incidents/{id}/primary-failure` (or `GET /api/incidents/{id}/primary-failure`): Analyze an incident and return the primary failure candidate, confidence, ranked scores, and symptom list.
  - `POST /api/incidents/analyze-primary-failure`: On-demand primary failure analysis for an arbitrary collection of evidence events.

## Incident Timeline

A unified, chronologically sorted incident timeline synthesizes 5 operational event sources across the incident lifetime:
- **Unified Event Sources**:
  1. `DEPLOYMENT`: Version changes and deployment lifecycle events leading up to or during the incident.
  2. `ANOMALY`: Statistical z-score and threshold deviation breaches.
  3. `SERVICE_FAILURE`: Correlated failure evidence chains.
  4. `METRIC`: Time-bucketed error rate and latency spikes.
  5. `LOG`: Structured operational log events across affected services (deduplicated against failure evidence).
- **Chronological Sorting & Deduplication**: Events are strictly sorted by timestamp ascending, formatted with unified metadata, summary, severity, and event type.
- **REST Endpoints**:
  - `GET /incidents/{id}/timeline` (or `GET /api/incidents/{id}/timeline`): Retrieve complete chronological timeline for an incident with optional `bufferMinutes` and `types` filtering.

## Historical Incident Dataset

A structured operational knowledge repository of real-world historical incidents and post-mortems persisted in PostgreSQL:
- **Coverage of 8 Operational Failure Categories**:
  1. `DATABASE_CONNECTION_EXHAUSTION`: Pool saturation, lock contention, unclosed transactions.
  2. `DEPLOYMENT_REGRESSION`: Serialization mismatches, missing configuration/secrets, incompatible timestamp formats.
  3. `SERVICE_UNAVAILABLE`: JVM OutOfMemoryError crashes, Kubernetes node evictions, readiness probe flapping.
  4. `NETWORK_LATENCY`: Cross-AZ interconnect packet loss, CoreDNS saturation, NAT Gateway port exhaustion.
  5. `MEMORY_PRESSURE`: Unbounded cache growth, ThreadLocal leaks in security contexts, large batch report memory starvation.
  6. `CACHE_FAILURE`: Redis TTL stampede storms, local in-memory cache desynchronization, token cache cold-start surges.
  7. `DEPENDENCY_TIMEOUT`: Third-party payment gateway HTTP socket hangs, downstream stock check latency, external webhook degradation.
  8. `MESSAGE_PROCESSING_FAILURE`: Kafka poison pill payloads, consumer group rebalance loops, consumer deserialization failures.
- **Incident Knowledge Schema**: Each record contains `incidentId`, `title`, `category`, `severity`, `symptoms`, `timeline`, `rootCause`, `resolution`, `affectedServices`, `prevention`, `occurredAt`, and `durationMinutes`.
- **PostgreSQL Persistence & Seeding**: Automatically seeds 24 canonical incident post-mortems idempotently on startup.
- **REST Endpoints**:
  - `GET /api/historical-incidents`: Query historical incidents with optional filtering by `category`, `service`, and full-text `query` search across symptoms and root cause.
  - `GET /api/historical-incidents/{id}`: Retrieve historical incident by numeric ID or code (e.g. `HIST-INC-001`).
  - `GET /api/historical-incidents/categories`: List all 8 failure categories.
  - `POST /api/historical-incidents`: Register a new historical operational record.
  - `POST /api/historical-incidents/seed`: Trigger dataset re-seeding into PostgreSQL.

## Future Phases

1. Extend the service domain APIs and PostgreSQL persistence.
2. Add service observability and event transport.
3. Build incident detection, correlation, and management workflows.
4. Add AI and retrieval capabilities for incident intelligence.
5. Build the React + Vite dashboard.
6. Add Docker Compose, deployment infrastructure, and end-to-end test scenarios.

This project is intentionally incremental: each assignment should add one coherent capability, preserve existing behavior, and update the relevant documentation and verification.

## Build

From the repository root:

```text
mvn verify
```

The current service shells require Java 21 or newer and Maven 3.9+.
