# Incident Engine

The Incident Engine processes, analyzes, and correlates operational events across business services.

## Modules

- `log-processor`: Consumes operational log events from the Kafka `application-logs` topic, validates required fields, rejects malformed payloads safely, and persists valid records to PostgreSQL (`application_logs` table).
