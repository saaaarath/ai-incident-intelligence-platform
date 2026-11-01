# Incident Engine

The Incident Engine processes, analyzes, and correlates operational events across business services.

## Modules

- `log-processor`: Consumes operational log events from the Kafka `application-logs` topic, validates required fields, rejects malformed payloads safely, and persists valid records to PostgreSQL (`application_logs` table).

## Log processor package map

The log processor uses feature-first packages. Related controllers, services,
entities, and repositories live together by capability instead of being split
into broad technical folders such as `controller` or `service`.

| Package | Responsibility |
| --- | --- |
| `ingestion` | Kafka log consumers, processed event entities, repositories, and log processing |
| `deployment` | Deployment event consumer, processing, and deployment API |
| `incident` | Incident lifecycle, correlation, evidence, and incident APIs |
| `anomaly` | Baselines, threshold/Z-score detection, and anomaly API |
| `telemetry` | Operational log query API |
| `metrics` | Metrics aggregation, latency calculations, and metrics API |
| `knowledge` | Historical incidents, runbooks, postmortems, and knowledge APIs |
| `knowledge.embedding` | Embedding providers, indexing, semantic retrieval, and embedding API |
| `rca` | Root-cause analysis providers, grounding, persistence, and RCA API |
| `fingerprint` | Error fingerprint generation and lookup |
| `timeline` | Incident timeline construction and timeline API |
| `dependency` | Service dependency graph and dependency API |
| `simulation` | Protected demo failure injection API |

This is a package reorganization only: REST paths, Kafka topics, persistence
models, and runtime behavior remain unchanged.
