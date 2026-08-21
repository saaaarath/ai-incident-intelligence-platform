# Infrastructure

The Docker Compose simulation starts PostgreSQL, Order Service, Payment Service, and Inventory Service with health-gated dependencies and an internal service network.

From the repository root, run:

```text
docker compose -f infrastructure/docker-compose.yml up --build
```

Order Service is published on host port `18080` by default. Set `ORDER_SERVICE_PORT` to use another available host port.
