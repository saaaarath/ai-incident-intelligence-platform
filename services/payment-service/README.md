# Payment Service

The Payment Service creates and retrieves payments through a Spring Web API backed by Spring Data JPA. It is intentionally independent from Order Service at this stage.

## API

- `POST /payments` with `{ "orderId": 42, "amount": 19.99 }` creates a payment in `PENDING` status.
- `GET /payments/{id}` retrieves an existing payment.

## PostgreSQL configuration

Set these environment variables before starting the service:

- `PAYMENT_DB_URL`
- `PAYMENT_DB_USERNAME`
- `PAYMENT_DB_PASSWORD`

The test profile uses an in-memory H2 database and does not require PostgreSQL.