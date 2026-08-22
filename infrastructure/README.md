# Infrastructure

The Docker Compose simulation starts PostgreSQL, Apache Kafka (KRaft), Order Service, Payment Service, Inventory Service, and Log Processor with health-gated dependencies and an internal service network.

## Topics

The `init-kafka` container automatically creates the following topics upon startup:
- `application-logs`
- `service-events`
- `deployment-events`

## Usage

From the repository root, run:

```text
docker compose -f infrastructure/docker-compose.yml up --build
```

- Order Service is published on host port `18080` by default. Set `ORDER_SERVICE_PORT` to use another available host port.
- Log Processor is published on host port `18084` by default. Set `LOG_PROCESSOR_PORT` to use another available host port.
- Kafka broker is published on host port `29092` by default. Set `KAFKA_PORT` to use another available host port. Internal services communicate over `kafka:9092` via `KAFKA_BOOTSTRAP_SERVERS`.
