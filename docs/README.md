# Documentation

## Implemented Capabilities
- Synchronous business services: Order Service, Payment Service, Inventory Service.
- Operational structured logging with standard JSON event schemas.
- Request correlation via `X-Trace-Id` HTTP header, filter extraction/generation, MDC binding, client interceptors, and cross-service operational log correlation.
- Controlled failure injection (`DB_FAILURE`, `LATENCY`, `SERVICE_UNAVAILABLE`, `ERROR_SPIKE`) via internal control API (`/internal/failures`) and configuration properties.
- Local infrastructure with Docker Compose: PostgreSQL and Apache Kafka (KRaft mode) with predefined topics (`application-logs`, `service-events`, `deployment-events`) and environment variable configuration across services.
- Incident Engine Log Processor: Consumes operational logs from Kafka (`application-logs`), validates required fields (`eventId`, `timestamp`, `service`, `level`, `eventType`, `traceId`, `message`), safely handles malformed events, and persists structured logs to PostgreSQL (`application_logs`).
- Deployment Events Pipeline: Supports `DEPLOYMENT_STARTED` and `DEPLOYMENT_COMPLETED` events, published to Kafka `deployment-events` topic, validated, and persisted into PostgreSQL (`deployment_events` table) alongside application events with idempotency guarantees.
- Operational Metrics Aggregation: Aggregation layer over persisted operational events (`application_logs`) calculating total events/requests, error counts, error rates, and latency metrics (`min`, `max`, `avg`, `p50`, `p95`, `p99`) across configurable fixed time windows (default: 1 minute) per service, exposed via `/api/metrics` REST endpoints.
- Baseline-Based Anomaly Detection: Statistical and threshold-based anomaly detection comparing current window operational metrics against historical baseline mean and variability (standard deviation). Emits and persists `AnomalyEvent` records (`metric`, `service`, `currentValue`, `baselineMean`, `baselineVariability`, `threshold`, `detectedAt`, `severity`) for controlled error spikes and latency degradation while suppressing false positives during normal operation.
- Z-Score Anomaly Detection: Statistical anomaly detection using standard Z-score analysis ($z = \frac{x - \mu}{\sigma}$) over windowed operational metrics, with safe zero-standard-deviation handling, configurable sensitivity thresholds (`anomaly.zscore.threshold`), and seamless incident integration.
- Incident Management: Automatically converts threshold-crossing anomalies into `Incident` entities (`id`, `title`, `severity`, `status`, `primaryService`, `startedAt`, `detectedAt`, `resolvedAt`) with lifecycle management (`OPEN`, `INVESTIGATING`, `RESOLVED`, `CLOSED`), dynamic severity upgrading, duplicate incident prevention, and REST APIs (`GET /incidents`, `GET /incidents/{id}`, `POST /incidents/{id}/acknowledge`, `POST /incidents/{id}/resolve`, `POST /incidents/{id}/close`, with multi-criteria filtering by status, severity, service, and time range).




