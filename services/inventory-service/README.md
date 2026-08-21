# Inventory Service

The Inventory Service retrieves inventory and reserves stock through a Spring Web API backed by Spring Data JPA. Reservation updates are transactionally locked per product.

## API

- `GET /inventory/{productId}` retrieves available stock.
- `POST /inventory/{productId}/reserve` with `{ "quantity": 2 }` reserves stock when enough quantity is available.

Insufficient stock returns HTTP `409 Conflict`; unknown products return HTTP `404 Not Found`.

## PostgreSQL configuration

Set these environment variables before starting the service:

- `INVENTORY_DB_URL`
- `INVENTORY_DB_USERNAME`
- `INVENTORY_DB_PASSWORD`

The test profile uses an in-memory H2 database and does not require PostgreSQL.