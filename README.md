# SwiftShip — Shipment Tracking Microservice

A production-quality shipment lifecycle management service built with **Spring Boot 3.2**, **PostgreSQL**, and **Apache Kafka**. Manages shipments from creation to final delivery, enforcing business rules through a Finite-State Machine and publishing reliable domain events via the Transactional Outbox pattern.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Shipment Lifecycle](#shipment-lifecycle)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)
- [How Events Reach Kafka](#how-events-reach-kafka)
- [Business Rules](#business-rules)
- [REST API](#rest-api)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Running Tests](#running-tests)
- [Monitoring](#monitoring)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    CLIENT / API GATEWAY                      │
│         Browser · E-Commerce Platform · Admin Dashboard      │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP
┌───────────────────────────▼─────────────────────────────────┐
│                   CONTROLLER LAYER                           │
│   ShipmentController          GlobalExceptionHandler         │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────┐  ┌──────────────────────────┐
│              SERVICE LAYER                    │  │   OutboxEventPublisher   │
│   ShipmentService                            │  │   @Scheduled every 5s    │
│   (FSM · Business Rules · Outbox write)      │  │   Polls DB → sends Kafka │
└───────────────────────────┬──────────────────┘  └──────────┬───────────────┘
                            │                                 │
┌───────────────────────────▼─────────────────────────────────▼───────────────┐
│                        REPOSITORY LAYER                                      │
│   ShipmentRepository · StatusHistoryRepository · OutboxEventRepository      │
│   CarrierRepository                                                          │
└───────────────────────────┬─────────────────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                    INFRASTRUCTURE                            │
│         PostgreSQL (+ Flyway)       Apache Kafka             │
│         DelayDetectionScheduler (hourly cron)                │
└─────────────────────────────────────────────────────────────┘
```

---

## Shipment Lifecycle

```
CREATED ──────────► PICKED_UP ──► IN_TRANSIT ──► OUT_FOR_DELIVERY ──► DELIVERED
   │                    │                                                   │
   └──► CANCELLED       └──► CANCELLED                          RETURNED (≤ 14 days)
```

| Transition | Allowed From | Notes |
|---|---|---|
| → PICKED_UP | CREATED | Carrier physically collected the shipment |
| → IN_TRANSIT | PICKED_UP | Shipment moving between hubs |
| → OUT_FOR_DELIVERY | IN_TRANSIT | Final delivery leg started |
| → DELIVERED | OUT_FOR_DELIVERY | Handed to customer — stamps `deliveredAt` |
| → CANCELLED | CREATED, PICKED_UP | Cannot cancel once in transit |
| → RETURNED | DELIVERED | Only within 14 days of `deliveredAt` |

> **DELAYED** is not a status — it is a computed boolean flag returned in API responses.  
> A shipment is DELAYED when it is not in a terminal state and `estimatedDeliveryDate` is more than 48 hours in the past.

---

## Tech Stack

| Component | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 3.2.4 |
| Database | PostgreSQL | 15 |
| Migrations | Flyway | (managed by Spring Boot) |
| Message Broker | Apache Kafka | 7.5.0 (Confluent) |
| ORM | Spring Data JPA / Hibernate | — |
| API Docs | SpringDoc OpenAPI (Swagger UI) | 2.3.0 |
| Code Generation | Lombok | — |
| Testing | JUnit 5 + Mockito + AssertJ | — |
| Monitoring | Prometheus + Grafana | latest |
| Containerisation | Docker + Docker Compose | — |

---

## Project Structure

```
src/
├── main/
│   ├── java/com/swiftship/
│   │   ├── App.java                          # Entry point (@SpringBootApplication)
│   │   ├── config/
│   │   │   ├── KafkaConfig.java              # Topic declaration + KafkaTemplate bean
│   │   │   └── OpenApiConfig.java            # Swagger UI metadata
│   │   ├── controller/
│   │   │   └── ShipmentController.java       # REST endpoints (/api/v1/shipments/**)
│   │   ├── dto/
│   │   │   ├── ShipmentResponse.java         # Response body for all shipment endpoints
│   │   │   ├── common/ErrorDetails.java      # Standardised error response shape
│   │   │   ├── create/CreateShipmentRequest.java
│   │   │   ├── update/UpdateShipmentStatusRequest.java
│   │   │   └── history/StatusHistoryResponse.java
│   │   ├── event/
│   │   │   └── ShipmentStatusChangedEvent.java  # Domain event (intermediate object → JSONB)
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java   # @RestControllerAdvice — maps exceptions to HTTP
│   │   │   ├── ShipmentNotFoundException.java          # → 404
│   │   │   ├── InvalidStatusTransitionException.java   # → 422
│   │   │   ├── CarrierCapacityExceededException.java   # → 409
│   │   │   └── ReturnWindowExpiredException.java       # → 422
│   │   ├── mapper/
│   │   │   └── ShipmentMapper.java           # Entity → DTO conversion
│   │   ├── model/
│   │   │   ├── Shipment.java                 # Core aggregate root
│   │   │   ├── ShipmentStatus.java           # FSM enum with transition rules
│   │   │   ├── ShipmentStatusHistory.java    # Immutable audit trail rows
│   │   │   ├── Carrier.java                  # Delivery driver entity
│   │   │   ├── Warehouse.java                # Hub/warehouse entity
│   │   │   └── OutboxEvent.java              # Transactional outbox rows
│   │   ├── repository/
│   │   │   ├── ShipmentRepository.java       # Includes custom JPQL queries
│   │   │   ├── ShipmentStatusHistoryRepository.java
│   │   │   ├── OutboxEventRepository.java
│   │   │   └── CarrierRepository.java
│   │   ├── service/
│   │   │   ├── ShipmentService.java          # Business logic + outbox write
│   │   │   ├── OutboxEventPublisher.java     # @Scheduled — polls outbox, sends to Kafka
│   │   │   └── DelayDetectionScheduler.java  # @Scheduled hourly — logs delayed shipments
│   │   └── utils/
│   │       ├── CommonUtils.java              # Interface: generateTrackingNumber()
│   │       ├── ValidateUtils.java            # Interface: capacity + return window checks
│   │       └── impl/
│   │           ├── CommonUtilsImpl.java
│   │           └── ValidateUtilsImpl.java
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           └── V1__initial_schema.sql        # Flyway — creates all tables + seed data
└── test/
    └── java/com/swiftship/
        ├── ShipmentServiceTest.java          # Unit tests for service layer
        └── ShipmentStatusFsmTest.java        # Parameterised FSM transition tests
```

---

## Database Schema

```
┌─────────────┐       ┌──────────────────────────┐       ┌────────────────────────┐
│  carriers   │       │        shipments          │       │      warehouses        │
│─────────────│       │──────────────────────────│       │────────────────────────│
│ id (PK)     │◄──────│ id (PK)                  │──────►│ id (PK)                │
│ name        │       │ tracking_number (UNIQUE)  │       │ name                   │
│ email       │       │ customer_name             │       │ city                   │
│ phone       │       │ customer_email            │       │ address                │
│ active      │       │ origin_address            │       └────────────────────────┘
└─────────────┘       │ destination_address       │
                      │ carrier_id (FK)           │
                      │ origin_warehouse_id (FK)  │
                      │ destination_warehouse_id  │
                      │ status (ENUM)             │
                      │ weight_kg                 │
                      │ estimated_delivery_date   │
                      │ delivered_at              │
                      └───────────┬──────────────┘
                                  │ 1-to-many
              ┌───────────────────┴────────────────┐
              │                                     │
┌─────────────▼──────────────┐    ┌────────────────▼────────────┐
│  shipment_status_history   │    │       outbox_events          │
│────────────────────────────│    │──────────────────────────────│
│ id (PK)                    │    │ id (PK)                      │
│ shipment_id (FK)           │    │ aggregate_id                 │
│ from_status                │    │ aggregate_type               │
│ to_status                  │    │ event_type                   │
│ notes                      │    │ payload (JSONB)              │
│ changed_by                 │    │ published (BOOLEAN)          │
│ changed_at                 │    │ published_at                 │
└────────────────────────────┘    └──────────────────────────────┘
```

> **Partial index on outbox:** `CREATE INDEX idx_outbox_unpublished ON outbox_events(published) WHERE published = FALSE`  
> Only unpublished rows are indexed — keeps polling fast as the table grows.

---

## How Events Reach Kafka

This service uses the **Transactional Outbox Pattern** to guarantee reliable event delivery.

### Why not publish to Kafka directly from the service?

```
// FRAGILE — direct publish
shipmentRepository.save(shipment);   // ✅ DB updated
kafkaTemplate.send(event);           // 💥 app crashes here → event lost forever
```

If the app crashes or Kafka is down between those two lines, the DB is updated but the event is never published. Downstream services (notifications, billing, analytics) miss the transition silently.

### The Outbox Solution — Two Phases

**Phase 1 — Inside `ShipmentService` (one `@Transactional`):**
```
① shipmentRepository.save()          ← update shipment status
② historyRepository.save()           ← write audit trail row
③ outboxEventRepository.save()       ← write outbox row (published = false)

All three commit atomically.
If the DB transaction fails → all three roll back together.
```

**Phase 2 — `OutboxEventPublisher` (scheduled every 5 seconds):**
```
④ SELECT * FROM outbox_events WHERE published = false ORDER BY created_at ASC
⑤ kafkaTemplate.send(topic, shipmentId, payload).get()   ← waits for broker ack
   Partition key = shipmentId → ordered delivery per shipment guaranteed
⑥ SET published = true, published_at = now()

On failure → log error, leave published = false → auto-retry on next poll
```

### Guarantees
- **Zero dual-write risk** — DB is the single source of truth
- **At-least-once delivery** — events survive Kafka downtime; rows stay unpublished until confirmed
- **Ordered per shipment** — `shipmentId` as partition key means all events for one shipment land on the same Kafka partition in creation order

### Kafka Topic

| Property | Value |
|---|---|
| Topic name | `shipment.events` |
| Partitions | 6 |
| Replicas | 3 |
| Partition key | `shipmentId` |

### Event Payload

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "shipmentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "trackingNumber": "SWF-1714978432000-A3B2C1",
  "previousStatus": "PICKED_UP",
  "newStatus": "IN_TRANSIT",
  "customerEmail": "customer@example.com",
  "carrierName": "Ali Driver",
  "notes": "Package collected from Riyadh hub",
  "occurredAt": "2026-04-17T10:00:00Z"
}
```

> **Consumer note:** `eventId` is unique per event. Because the outbox provides at-least-once delivery, consumers must be idempotent — track processed `eventId` values and skip duplicates.

---

## Business Rules

| Rule | Detail |
|---|---|
| **Forward-only lifecycle** | Shipments can only move forward through statuses (no going back) |
| **Carrier capacity** | A carrier may hold at most **20 active shipments** at any time (active = not DELIVERED / CANCELLED / RETURNED) |
| **Cancellation window** | CANCELLED is only allowed from `CREATED` or `PICKED_UP` — once in transit it cannot be cancelled |
| **Return window** | RETURNED is only allowed within **14 days** of `deliveredAt` |
| **DELAYED flag** | Computed at read time — `true` when not in a terminal state and `estimatedDeliveryDate` is **> 48 hours** in the past. Never stored in the database. |

All limits are configurable via `application.yml` — no code change needed:

```yaml
swiftship:
  business:
    max-active-shipments-per-carrier: 20
    return-window-days: 14
    delay-threshold-hours: 48
```

---

## REST API

Base URL: `http://localhost:8080/api/v1`  
Interactive docs: **http://localhost:8080/swagger-ui.html**

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/shipments` | Create a new shipment |
| `GET` | `/shipments` | List all shipments (paginated, optional `?status=`) |
| `GET` | `/shipments/{id}` | Get shipment by UUID |
| `GET` | `/shipments/track/{trackingNumber}` | Public tracking by tracking number |
| `PATCH` | `/shipments/{id}/status` | Transition status (FSM enforced) |
| `PATCH` | `/shipments/{id}/carrier` | Assign / re-assign a carrier |
| `GET` | `/shipments/{id}/history` | Full status transition audit trail |
| `GET` | `/shipments/delayed` | All DELAYED shipments |
| `GET` | `/shipments/carrier/{carrierId}` | Shipments by carrier (paginated) |

### Pagination

All list endpoints support standard Spring Pageable parameters:

```
GET /api/v1/shipments?page=0&size=20&sort=createdAt,desc
GET /api/v1/shipments?status=IN_TRANSIT&page=0&size=10
```

### Error Response Shape

Every error returns the same structure:

```json
{
  "status": 422,
  "error": "Invalid Status Transition",
  "message": "Cannot transition shipment from CREATED to DELIVERED. Allowed transitions from CREATED: [PICKED_UP, CANCELLED]",
  "timestamp": "2026-04-17T10:00:00Z"
}
```

| HTTP Status | When |
|---|---|
| `400` | Validation failure — `details` field lists field-level errors |
| `404` | Shipment or carrier not found |
| `409` | Carrier has reached 20 active shipments |
| `422` | Invalid FSM transition or return window expired |
| `500` | Unexpected server error |

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker + Docker Compose

### 1. Clone and start infrastructure

```bash
git clone https://github.com/swiftship/shipment-tracking.git
cd shipment-tracking

# Start PostgreSQL, Kafka, Zookeeper, Kafka UI, Prometheus, Grafana
docker-compose up -d postgres kafka zookeeper kafka-ui
```

### 2. Run the application

```bash
mvn spring-boot:run
```

The app starts on **http://localhost:8080**.  
Flyway applies `V1__initial_schema.sql` automatically on startup.  
The Kafka topic `shipment.events` is created automatically on first run.

### 3. Run the full stack (app + all services)

```bash
docker-compose up -d
```

Wait for the health checks to pass (~60 seconds), then visit:

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Kafka UI | http://localhost:8085 |
| Grafana | http://localhost:3000 (admin / admin) |
| Prometheus | http://localhost:9090 |
| Health check | http://localhost:8080/actuator/health |

### 4. Create your first shipment

```bash
# Create a carrier first (insert via DB or add a carrier endpoint)
# Then create a shipment:

curl -X POST http://localhost:8080/api/v1/shipments \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Omar Hassan",
    "customerEmail": "omar@example.com",
    "originAddress": "King Fahd Road, Riyadh 12271",
    "destinationAddress": "Al Corniche Road, Jeddah 23521",
    "carrierId": "<carrier-uuid>",
    "weightKg": 2.5,
    "estimatedDeliveryDate": "2026-04-25"
  }'
```

```bash
# Track a shipment
curl http://localhost:8080/api/v1/shipments/track/SWF-1714978432000-A3B2C1

# Move to PICKED_UP
curl -X PATCH http://localhost:8080/api/v1/shipments/<id>/status \
  -H "Content-Type: application/json" \
  -d '{
    "status": "PICKED_UP",
    "notes": "Package collected from warehouse",
    "changedBy": "ali.driver@swiftship.com"
  }'

# Get full history
curl http://localhost:8080/api/v1/shipments/<id>/history
```

---

## Configuration

All configuration is in `src/main/resources/application.yml`. Key properties:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/swiftship
    username: ${DB_USER:swiftship}       # override with env var DB_USER
    password: ${DB_PASS:swiftship}

  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}

swiftship:
  kafka:
    topics:
      shipment-events: shipment.events   # Kafka topic name

  business:
    max-active-shipments-per-carrier: 20 # carrier capacity limit
    return-window-days: 14               # days after delivery to allow return
    delay-threshold-hours: 48            # hours past estimated date = DELAYED
```

### Environment Variables (Docker / Production)

| Variable | Default | Description |
|---|---|---|
| `DB_USER` | `swiftship` | PostgreSQL username |
| `DB_PASS` | `swiftship` | PostgreSQL password |
| `KAFKA_BROKERS` | `localhost:9092` | Kafka bootstrap servers |
| `LOG_LEVEL` | `INFO` | Root log level |

---

## Running Tests

```bash
# Run all tests
mvn test

# Run only the FSM transition tests
mvn test -Dtest=ShipmentStatusFsmTest

# Run only the service unit tests
mvn test -Dtest=ShipmentServiceTest
```

### What Is Tested

| Test Class | Coverage |
|---|---|
| `ShipmentStatusFsmTest` | All valid transitions allowed · All invalid transitions rejected · Terminal states have no outgoing transitions |
| `ShipmentServiceTest` | Create shipment saves outbox event · Carrier capacity rejection · All key FSM transitions · Return window enforcement · DELAYED flag logic (5 scenarios) · `outboxEventRepository.save()` called exactly once per transition |

Tests use **H2 in-memory** for the database scope and **Mockito** for all dependencies — no Docker required.

---

## Monitoring

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "kafka": { "status": "UP" }
  }
}
```

### Metrics

Available at `http://localhost:8080/actuator/metrics`

Exposed to Prometheus at `http://localhost:8080/actuator/prometheus`

### Grafana

Import the default Spring Boot dashboard (ID: `6756`) in Grafana at `http://localhost:3000` to visualise JVM metrics, HTTP request rates, and Kafka producer stats.

### Kafka UI

Browse the `shipment.events` topic, inspect individual event payloads, and verify outbox publishing at **http://localhost:8085**.

---

## Key Design Decisions

**FSM in the domain model** — `ShipmentStatus` enum owns all transition rules via `canTransitionTo()`. Business rules live in the domain, not scattered across service methods.

**Transactional Outbox** — shipment update, history write, and outbox event write happen in one ACID transaction. `OutboxEventPublisher` polls every 5 seconds and forwards to Kafka — zero dual-write risk, guaranteed delivery even during Kafka downtime.

**DELAYED as a computed flag** — never stored in the database. Calculated at read time via `Shipment.isDelayed()` — no background job needed, and changing the threshold requires no migration.

**PostgreSQL** — chosen because ACID transactions are required for the outbox pattern, the data model is genuinely relational with enforced FK constraints, and `JSONB` provides queryable event payloads in `outbox_events`.

**Partial index on outbox** — `WHERE published = FALSE` keeps the polling index small as the table grows, since published rows vastly outnumber unpublished ones in steady state.
