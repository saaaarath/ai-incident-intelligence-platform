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
  - `POST /api/anomalies/detect`: Trigger anomaly detection over specified time windows and persist detected anomaly events.

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
