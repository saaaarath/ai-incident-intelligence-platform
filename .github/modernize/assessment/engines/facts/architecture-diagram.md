# Architecture Diagram

The AI Incident Intelligence Platform is a Java and React monorepo with a synchronous business transaction path and an asynchronous operational intelligence path. The business services emit correlated operational events; the incident engine stores, analyzes, retrieves, and presents incident context through REST APIs.

## Application Architecture

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
~~~mermaid
flowchart TD
    subgraph ClientLayer["Client Layer"]
        Browser["React dashboard"]
        Operator["SRE operator"]
    end
    subgraph BusinessLayer["Business Services - Spring Boot"]
        Order["Order service"]
        Payment["Payment service"]
        Inventory["Inventory service"]
        Shared["Operational logging library"]
    end
    subgraph EventLayer["Event and Processing Layer"]
        Kafka["Apache Kafka KRaft"]
        Processor["Log processor"]
        Metrics["Metrics aggregation"]
        Detector["Anomaly detection"]
        Incidents["Incident correlation"]
    end
    subgraph IntelligenceLayer["Intelligence Layer"]
        Retrieval["Semantic retrieval"]
        RCA["AI root cause analysis"]
        Knowledge["Runbooks and postmortems"]
    end
    subgraph DataLayer["Persistence Layer"]
        Postgres[("PostgreSQL 16")]
        Vector[("pgvector")]
    end
    subgraph ExternalLayer["External Providers"]
        Gemini["Gemini embeddings and LLM"]
    end

    Operator -->|"uses"| Browser
    Browser -->|"REST queries and actions"| Processor
    Order -->|"REST payment call"| Payment
    Order -->|"REST reservation call"| Inventory
    Order -->|"uses"| Shared
    Payment -->|"uses"| Shared
    Inventory -->|"uses"| Shared
    Shared -->|"publishes logs and deployments"| Kafka
    Kafka -->|"consumes events"| Processor
    Processor -->|"stores events and deployments"| Postgres
    Processor -->|"calculates windows"| Metrics
    Metrics -->|"feeds baselines"| Detector
    Detector -->|"creates signals"| Incidents
    Incidents -->|"persists incidents and evidence"| Postgres
    Processor -->|"loads incident context"| Retrieval
    Knowledge -->|"stores operational knowledge"| Postgres
    Retrieval -->|"vector search"| Vector
    Retrieval -->|"uses indexed documents"| Knowledge
    RCA -->|"requests embeddings and analysis"| Gemini
    RCA -->|"reads context"| Retrieval
    RCA -->|"stores reports"| Postgres
    Browser -->|"failure simulation and investigation"| Processor
~~~

### Technology Stack Summary

| Layer | Technology | Version | Purpose |
|---|---|---:|---|
| Business services | Java and Spring Boot | 3.3.5, Java 21 | Independently deployable order, payment, and inventory APIs |
| Incident engine | Spring Boot Web, Validation, Data JPA | 3.3.5 | REST APIs, persistence, validation, and analysis orchestration |
| Frontend | React and Vite | React 18.3.1, Vite 6.2.0 | Operator dashboard for incidents, metrics, knowledge, RCA, and chaos |
| Messaging | Apache Kafka in KRaft mode | 3.7.0 | Durable asynchronous operational and deployment events |
| Relational storage | PostgreSQL | 16 | Business data, events, incidents, knowledge, and RCA reports |
| Vector storage | pgvector extension | 0.1.6 client | Embedding persistence and similarity retrieval |
| AI provider | Gemini embeddings and chat completion API | Configurable | Embeddings and grounded RCA generation |
| Build and packaging | Maven multi-module build | Root aggregator | Shared library and Java service builds |
| Local orchestration | Docker Compose | Configured in repository | Health-gated local service network and persistent volumes |

### Data Storage & External Services

PostgreSQL is shared by the local Compose deployment, with service-specific tables and incident-engine tables in the same database. Kafka topics `application-logs`, `service-events`, and `deployment-events` decouple event producers from the log processor. The pgvector extension stores document chunks and embeddings for semantic retrieval. The incident engine optionally calls Gemini for embedding generation and RCA; API keys and provider URLs are externalized through environment-backed properties.

### Key Architectural Decisions

- Business transactions use synchronous REST calls from Order Service to Payment Service and Inventory Service, while operational telemetry is published asynchronously so logging does not define the transaction response path.
- A shared operational-logging module standardizes JSON events, trace propagation, failure injection, Kafka publishing, and deployment event publishing across business services.
- The log processor centralizes incident intelligence: it persists raw signals, aggregates metrics, detects anomalies, correlates incidents, retrieves historical knowledge, and persists grounded RCA reports.

## Component Relationships

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
~~~mermaid
flowchart LR
    subgraph cPresentation["Presentation"]
        cDashboard["React dashboard"]
        cIncidentController["Incident controllers"]
        cDomainControllers["Domain controllers"]
    end
    subgraph cBusiness["Business Logic"]
        cOrderService["Order service"]
        cPaymentService["Payment service"]
        cInventoryService["Inventory service"]
        cLogService["Log processor service"]
        cMetricService["Metrics service"]
        cAnomalyService["Anomaly service"]
        cIncidentService["Incident service"]
        cRcaEngine["RCA engine"]
    end
    subgraph cData["Data Access"]
        cBusinessRepos["Business repositories"]
        cEventRepos["Event repositories"]
        cIncidentRepos["Incident repositories"]
        cKnowledgeRepos["Knowledge repositories"]
        cRcaRepo["RCA report repository"]
    end
    subgraph cInfrastructure["Infrastructure and Cross Cutting"]
        cRestClients["Downstream REST clients"]
        cKafkaPublisher["Kafka log publisher"]
        cKafkaConsumers["Kafka consumers"]
        cTrace["Trace and logging filters"]
        cFailure["Failure injection filter"]
    end

    cDashboard -->|"calls REST"| cIncidentController
    cDashboard -->|"calls REST"| cDomainControllers
    cIncidentController -->|"delegates"| cIncidentService
    cIncidentController -->|"builds context"| cRcaEngine
    cDomainControllers -->|"delegates"| cLogService
    cDomainControllers -->|"delegates"| cMetricService
    cDomainControllers -->|"delegates"| cAnomalyService
    cDomainControllers -->|"delegates"| cRcaEngine
    cOrderService -->|"calls"| cRestClients
    cOrderService -->|"persists"| cBusinessRepos
    cPaymentService -->|"persists"| cBusinessRepos
    cInventoryService -->|"persists"| cBusinessRepos
    cOrderService -->|"emits"| cKafkaPublisher
    cPaymentService -->|"emits"| cKafkaPublisher
    cInventoryService -->|"emits"| cKafkaPublisher
    cKafkaConsumers -->|"dispatches"| cLogService
    cKafkaConsumers -->|"dispatches"| cIncidentService
    cLogService -->|"writes"| cEventRepos
    cMetricService -->|"reads"| cEventRepos
    cAnomalyService -->|"reads metrics"| cEventRepos
    cIncidentService -->|"writes evidence"| cIncidentRepos
    cRcaEngine -->|"reads context"| cIncidentRepos
    cRcaEngine -->|"reads knowledge"| cKnowledgeRepos
    cRcaEngine -->|"writes report"| cRcaRepo
    cTrace -.->|"intercepts requests"| cPresentation
    cFailure -.->|"injects controlled faults"| cBusiness
~~~

### Component Inventory

| Component | Layer | Type | Responsibility |
|---|---|---|---|
| `App` and `incidentApi` | Presentation | React component and API client | Poll incidents, route views, and invoke backend REST endpoints |
| `IncidentController` | Presentation | Spring REST controller | Incident listing, state transitions, evidence, context, and correlation endpoints |
| Domain controllers | Presentation | Spring REST controllers | Metrics, anomalies, dependencies, logs, deployments, runbooks, knowledge, embeddings, postmortems, and failures |
| `OrderController` | Presentation | Spring REST controller | Creates and retrieves orders |
| `OrderService` | Business logic | Spring service | Orchestrates payment, inventory reservation, state changes, and operational events |
| `PaymentService` | Business logic | Spring service | Creates and retrieves payments |
| `InventoryService` | Business logic | Spring service | Reserves inventory with transactional protection |
| `LogEventConsumer` | Infrastructure | Kafka listener | Receives application log JSON from `application-logs` |
| `DeploymentEventConsumer` | Infrastructure | Kafka listener | Receives deployment lifecycle events |
| `LogProcessorService` | Business logic | Spring service | Validates and persists raw operational log events |
| `MetricsAggregationService` | Business logic | Spring service | Builds fixed-window service metrics and latency statistics |
| `AnomalyDetectionService` | Business logic | Spring service | Applies absolute threshold and z-score baseline detection |
| `IncidentCorrelationService` | Business logic | Spring service | Correlates anomalies and evidence into incidents |
| `RcaContextBuilder` | Business logic | Spring service | Builds bounded evidence context for analysis |
| `LlmRcaEngine` | Business logic | Spring service | Calls the configured LLM and validates grounded RCA reports |
| JPA repositories | Data access | Spring Data repositories | Persist business entities, events, incidents, knowledge, and RCA reports |
| `RestPaymentClient` and `RestInventoryClient` | Infrastructure | REST clients | Propagate trace IDs and enforce downstream timeouts |
| `KafkaLogPublisher` | Infrastructure | Kafka producer | Publishes structured logs and deployment events asynchronously |
| Trace filters and structured logger | Infrastructure | Cross-cutting | Create or propagate `X-Trace-Id` and attach it to events |
| Failure injection components | Infrastructure | Filter, service, and controller | Simulate database failure, latency, unavailability, and error spikes |

## Operational Workflows

### Workflow 1: Order creation and telemetry

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
~~~mermaid
flowchart LR
    wClient["Client"] -->|"POST order"| wOrder["Order controller"]
    wOrder -->|"orchestrates"| wService["Order service"]
    wService -->|"create payment"| wPayment["Payment service"]
    wPayment -->|"payment result"| wService
    wService -->|"reserve stock"| wInventory["Inventory service"]
    wInventory -->|"reservation result"| wService
    wService -->|"save status"| wOrderDb[("Order database")]
    wService -->|"publish structured events"| wKafka["Kafka application logs"]
    wPayment -->|"publish events"| wKafka
    wInventory -->|"publish events"| wKafka
    wService -->|"return response"| wClient
~~~

1. The incoming request receives or creates an `X-Trace-Id`.
2. Order Service saves the order, calls Payment Service, then calls Inventory Service.
3. Downstream failures are converted into order failure state and structured failure events.
4. The response returns synchronously while event publication and processing continue asynchronously.

### Workflow 2: Event processing to incident

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
~~~mermaid
flowchart TD
    w2Kafka["Kafka topics"] -->|"consume logs"| w2Consumer["Kafka consumers"]
    w2Consumer -->|"validate and normalize"| w2Processor["Log processor"]
    w2Processor -->|"persist raw events"| w2Logs[("Application logs")]
    w2Logs -->|"aggregate windows"| w2Metrics["Metrics aggregation"]
    w2Metrics -->|"compare baseline"| w2Detect["Anomaly detection"]
    w2Detect -->|"create or correlate"| w2Incident["Incident correlation"]
    w2Incident -->|"persist incident and evidence"| w2IncidentDb[("Incident data")]
    w2Incident -->|"serve investigation APIs"| w2Dashboard["Dashboard"]
~~~

1. `LogEventConsumer` and `DeploymentEventConsumer` receive Kafka records.
2. The processor validates payloads and persists application and deployment events.
3. Metrics are calculated over fixed windows, then threshold and z-score detectors compare current values with historical baselines.
4. Qualifying anomalies are correlated into incidents with evidence and lifecycle state.

### Workflow 3: Investigation and AI RCA

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
~~~mermaid
flowchart TD
    w3User["SRE operator"] -->|"select incident"| w3Dashboard["Dashboard"]
    w3Dashboard -->|"request context"| w3Context["RCA context builder"]
    w3Context -->|"load evidence"| w3Evidence[("Events and incidents")]
    w3Context -->|"retrieve similar knowledge"| w3Retrieve["Semantic retrieval"]
    w3Retrieve -->|"search embeddings"| w3Vector[("pgvector")]
    w3Context -->|"request analysis"| w3Engine["LLM RCA engine"]
    w3Engine -->|"generate grounded report"| w3Provider["Gemini provider"]
    w3Engine -->|"persist report"| w3Report[("RCA reports")]
    w3Report -->|"display findings"| w3Dashboard
~~~

1. The dashboard requests incident evidence, timeline, similar incidents, runbooks, and RCA context through the log processor API.
2. The context builder bounds the evidence window and retrieves semantically similar knowledge from pgvector.
3. The LLM RCA engine sends the structured context to the configured provider, validates the response, and persists the report.
4. The dashboard displays the persisted analysis and supports incident acknowledgement, resolution, closure, failure simulation, and runbook actions.

## HLD and LLD Reading Guide

- **HLD:** Read `Application Architecture` left to right for deployment boundaries, infrastructure, data stores, and external providers.
- **LLD:** Read `Component Relationships` left to right for controller to service to repository dependencies and cross-cutting interception.
- **Workflow:** Use the three workflows to understand runtime sequencing, asynchronous boundaries, trace propagation, and the AI investigation loop.
- **Primary runtime boundary:** `incident-engine/log-processor` is the current integration boundary for dashboard APIs and incident intelligence; `ai-engine` remains reserved as a future module.
