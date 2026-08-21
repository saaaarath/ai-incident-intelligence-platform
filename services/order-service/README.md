# Order Service

The Order Service creates and retrieves orders through a Spring Web API backed by Spring Data JPA.

## API

- `POST /orders` with `{ "customerId": "..." }` creates an order in `CREATED` status.
- `GET /orders/{id}` retrieves an existing order.

## PostgreSQL configuration

Set these environment variables before starting the service:

- `ORDER_DB_URL`
- `ORDER_DB_USERNAME`
- `ORDER_DB_PASSWORD`

The test profile uses an in-memory H2 database and does not require PostgreSQL.