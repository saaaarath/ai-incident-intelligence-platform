# Documentation

## Implemented Capabilities
- Synchronous business services: Order Service, Payment Service, Inventory Service.
- Operational structured logging with standard JSON event schemas.
- Request correlation via `X-Trace-Id` HTTP header, filter extraction/generation, MDC binding, client interceptors, and cross-service operational log correlation.
- Controlled failure injection (`DB_FAILURE`, `LATENCY`, `SERVICE_UNAVAILABLE`, `ERROR_SPIKE`) via internal control API (`/internal/failures`) and configuration properties.

