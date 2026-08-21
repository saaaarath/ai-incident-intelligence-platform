# AI Incident Intelligence Platform

The AI Incident Intelligence Platform is an incremental monorepo for exploring operational intelligence across business services. The platform will eventually collect service signals, analyze incidents, and present useful operational context to engineering teams.

## Architecture

- `services/` contains independently deployable Java and Spring Boot services for orders, payments, and inventory.
- `incident-engine/` is reserved for future incident processing and correlation.
- `ai-engine/` is reserved for future AI and retrieval workflows.
- `dashboard/` is reserved for a future React + Vite operator interface.
- `infrastructure/` is reserved for future Docker Compose and deployment assets.
- `test-scenarios/` is reserved for future end-to-end and failure scenarios.
- `docs/` contains project and phase documentation.
- PostgreSQL is the persistence technology for the Order Service and is configured through environment variables.

The root Maven project is an aggregator for the current Java services. Each service owns its own Spring Boot application and build configuration so later assignments can evolve services independently.

## Current Status

Order Service, Payment Service, and Inventory Service are independently functional. They provide JPA-backed APIs with PostgreSQL configuration and isolated H2 integration tests. Inventory reservations use transactional row locking to prevent stock from being oversold. There is no Kafka, AI, RAG, anomaly detection, incident management, or dashboard functionality yet.

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
