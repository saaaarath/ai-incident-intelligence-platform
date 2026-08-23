# Documentation

## Implemented Capabilities
- Synchronous business services: Order Service, Payment Service, Inventory Service.
- Operational structured logging with standard JSON event schemas.
- Request correlation via `X-Trace-Id` HTTP header, filter extraction/generation, MDC binding, client interceptors, and cross-service operational log correlation.
- Controlled failure injection (`DB_FAILURE`, `LATENCY`, `SERVICE_UNAVAILABLE`, `ERROR_SPIKE`) via internal control API (`/internal/failures`) and configuration properties.
- Local infrastructure with Docker Compose: PostgreSQL and Apache Kafka (KRaft mode) with predefined topics (`application-logs`, `service-events`, `deployment-events`) and environment variable configuration across services.
- Incident Engine Log Processor: Consumes operational logs from Kafka (`application-logs`), validates required fields (`eventId`, `timestamp`, `service`, `level`, `eventType`, `traceId`, `message`), safely handles malformed events, and persists structured logs to PostgreSQL (`application_logs`).
- Deployment Events Pipeline: Supports `DEPLOYMENT_STARTED` and `DEPLOYMENT_COMPLETED` events, published to Kafka `deployment-events` topic, validated, and persisted into PostgreSQL (`deployment_events` table) alongside application events with idempotency guarantees.
