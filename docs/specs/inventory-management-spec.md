# Inventory Management — Specification

> **Phase:** SDD Spec
> **Context:** Inventory Management (Bounded Context)
> **Date:** 2026-06-18
> **Status:** Draft — ready for design phase
> **Schema:** `inventory`
> **Domain Type:** CORE DOMAIN — every other context depends on this one

---

## Table of Contents

1. [Purpose](#1-purpose)
2. [Domain Model](#2-domain-model)
3. [Invariants & Business Rules](#3-invariants--business-rules)
4. [REST API Specification](#4-rest-api-specification)
5. [Query Specifications](#5-query-specifications)
6. [Validation Rules](#6-validation-rules)
7. [Domain Events](#7-domain-events)
8. [FEFO Allocation Engine](#8-fefo-allocation-engine)
9. [Batch Status Transitions](#9-batch-status-transitions)
10. [Concurrency Strategy](#10-concurrency-strategy)
11. [Key Scenarios](#11-key-scenarios)
12. [Frontend Specifications](#12-frontend-specifications)
13. [Testing Requirements](#13-testing-requirements)
14. [Error Handling](#14-error-handling)

---

## 1. Purpose

The Inventory Management context is the **core domain** of MediStock. It tracks pharmaceutical stock at the batch/lot level, enforces FEFO (First Expiry, First Out) picking, maintains an immutable movement audit trail, and provides stock projections for consumption by all other contexts.

**Schema namespace:** `inventory` (schema-per-context).

**Multi-tenancy:** Shared database + shared schema. `tenant_id` on every business table. Application-layer isolation. `tenant_id` is inferred from the JWT token, never from request body.

### In Scope (this spec)

- Batch aggregate (core entity with lifecycle)
- Movement aggregate (immutable audit log)
- StockProjection (event-updated read model)
- FEFO allocation engine (AllocationStrategy, FefoAllocationStrategy, BatchAllocator)
- Batch receiving (goods receipt process)
- Inventory adjustments (cycle count corrections)
- Expiration handling (status transitions, NEAR_EXPIRY → EXPIRED → WRITTEN_OFF)
- Optimistic locking strategy for concurrent picks
- Concurrency scenarios and race condition handling
- Domain events (BatchReceived, BatchExpired, BatchNearExpiry, BatchDepleted, BatchQuarantined, MovementRecorded, StockDepleted, StockReplenished)
- REST API contracts
- Frontend components
- Flyway V2 migration
- Full TDD strategy

### Out of Scope (handled by other contexts or post-MVP)

- Sales FEFO allocation (Sales context consumes the FEFO engine, does not own it)
- Alert evaluation (Alerting context)
- Returns management — all returns enter quarantine by default, manual quality validation before FEFO re-entry
- POS integration
- Cold chain tracking
- Serial number tracking
- Advanced stock states (quarantine, reserved, in-transit) — post-MVP
- Partitioning / archival for movements — post-MVP
- Pessimistic locking — optimistic locking only

### Cross-Context Dependencies

**Depends on:** Product Catalog context (product references, product existence validation).

**Consumed by:**
- **Sales context:** Consumes FEFO allocation engine for sale-time batch picking.
- **Alerting context:** Consumes domain events (BatchExpired, BatchNearExpiry, MovementRecorded, StockDepleted, StockReplenished).
- **Reporting context:** Reads stock projections and movement data.

---

## 2. Domain Model

### 2.1 Batch Aggregate Root

Batch is the **most important aggregate** in MediStock. It represents a specific manufacturing run of a pharmaceutical product with a unique lot number and expiration date.

```
Batch (AGGREGATE ROOT)
├── batchId: UUID                 (PK, generated)
├── tenantId: string              (NOT optional — multi-tenant)
├── productId: UUID               (reference to Product in Product Catalog)
├── lotNumber: string             (manufacturer-assigned, required)
├── manufacturingDate: LocalDate  (optional — manufacturer provides)
├── expirationDate: LocalDate     (MANDATORY — core business field)
├── initialQuantity: int          (received quantity, > 0)
├── currentQuantity: int          (remaining quantity, ≥ 0, never negative)
├── locationId: UUID              (FK to inventory.locations, required)
├── status: BatchStatus           (see enum below)
├── costPerUnit: BigDecimal       (monetary value at time of receipt, > 0)
├── receivedDate: LocalDateTime   (when it entered the system)
├── notes: String                 (optional)
└── version: long                 (⚠️ optimistic lock — see INV-DEC-03)
```

#### BatchStatus Enum

| Status | Description | Transitions To |
|--------|-------------|---------------|
| `ACTIVE` | Available for sale/picking | NEAR_EXPIRY, QUARANTINED, DEPLETED, WRITTEN_OFF |
| `NEAR_EXPIRY` | Within configurable warning window (e.g., 90 days before exp) | EXPIRED, QUARANTINED, DEPLETED, WRITTEN_OFF |
| `EXPIRED` | Past expiration date, auto-quarantined | WRITTEN_OFF |
| `QUARANTINED` | Manually or automatically blocked | WRITTEN_OFF (reactivation is post-MVP — see INV-DEC-08) |
| `DEPLETED` | currentQuantity = 0 | Terminal (no further transitions) |
| `WRITTEN_OFF` | Officially written off with documentation | Terminal (no further transitions) |

#### Design Decision: version in domain

**Decision INV-DEC-03:** The `version` field is present in the domain Batch entity AND the JPA entity. Unlike Product Catalog (PC-DEC-09 where version is strictly JPA-only), the FEFO allocation engine needs to detect concurrent modifications in the application layer to drive retry logic. The domain Batch exposes `getVersion()` for the `BatchAllocator` to read; the JPA `@Version` annotation provides the infrastructure enforcement. The domain layer treats version as opaque — it does not set or manipulate it — but the application layer uses it for concurrency control.

#### Key Invariants

| Rule ID | Rule | Enforcement |
|---------|------|-------------|
| INV-R01 | expirationDate MUST be in the future at receipt time | Domain validation on receive |
| INV-R02 | currentQuantity MUST NEVER be negative | Validated on every OUT movement |
| INV-R03 | currentQuantity MUST be ≤ initialQuantity | Invariant check |
| INV-R04 | lotNumber + productId + expirationDate is a unique business key per tenant | DB unique constraint on (tenant_id, product_id, lot_number, expiration_date) |
| INV-R05 | An EXPIRED batch MUST NOT be picked for sale | FEFO query excludes expired batches |
| INV-R06 | NEAR_EXPIRY status is calculated, not set manually | Computation based on configurable threshold |
| INV-R07 | A DEPLETED batch (currentQuantity = 0) automatically transitions status | Domain event BatchDepleted |
| INV-R08 | EXPIRED status is set automatically when expirationDate passes current date | Scheduled job or event |
| INV-R09 | WRITTEN_OFF batches cannot be reactivated | Status transition validation |
| INV-R10 | QUARANTINED batches cannot be picked for sale | FEFO query excludes QUARANTINED |
| INV-R11 | initialQuantity > 0 at receipt time | Validation |
| INV-R12 | costPerUnit > 0 at receipt time | Validation |

### 2.2 Movement Aggregate (Immutable)

Movement is an append-only, immutable record of every stock change. It is the audit trail.

```
Movement (AGGREGATE ROOT) — IMMUTABLE
├── movementId: UUID              (PK, generated)
├── tenantId: string              (multi-tenant)
├── movementType: MovementType    (RECEIPT | SALE | RETURN | TRANSFER_IN | TRANSFER_OUT | ADJUSTMENT | WRITE_OFF)
├── direction: MovementDirection  (IN | OUT)
├── quantity: int                 (always positive; direction indicates sign)
├── productId: UUID               (reference to Product)
├── batchId: UUID                 (nullable — for non-batch-specific adjustments)
├── referenceType: string         (e.g. "PURCHASE_ORDER", "SALE", "CYCLE_COUNT")
├── referenceId: UUID             (FK to originating document)
├── unitPrice: BigDecimal         (price at time of movement, for valuation)
├── totalValue: BigDecimal        (unitPrice × quantity)
├── reason: string                (required for ADJUSTMENT and WRITE_OFF)
├── performedBy: string           (userId)
├── performedAt: LocalDateTime    (when the physical movement happened)
├── recordedAt: LocalDateTime     (when entered in system)
├── notes: string                 (optional)
└── source: string                (UI | API | BATCH | SYSTEM)
```

#### MovementType Enum

| Type | Direction | Description |
|------|-----------|-------------|
| `RECEIPT` | IN | Purchase order receipt |
| `SALE` | OUT | Sale to customer |
| `RETURN` | IN | Customer return |
| `TRANSFER_IN` | IN | Received from another location |
| `TRANSFER_OUT` | OUT | Moved to another location |
| `ADJUSTMENT` | IN or OUT | Cycle count correction |
| `WRITE_OFF` | OUT | Expired/damaged disposal |

#### Movement Business Rules

| Rule ID | Rule | Enforcement |
|---------|------|-------------|
| INV-R13 | Movements are IMMUTABLE — no UPDATE or DELETE allowed | DB-level restriction (no update/delete privileges for app user) |
| INV-R14 | Every OUT movement MUST reference a batchId (traceability) | Domain validation |
| INV-R15 | quantity is always positive; direction indicates sign | Domain invariant |
| INV-R16 | An ADJUSTMENT movement requires a reason | Validation |
| INV-R17 | performedAt MUST be ≤ recordedAt (cannot record future movements) | Domain validation |
| INV-R18 | A RECEIPT movement MUST create the associated Batch | Transactional boundary |

### 2.3 StockProjection (Read Model)

StockProjection is **NOT an aggregate**. It is a **materialized read model** updated by domain events. It is the source of truth for "current stock position" queries.

```
StockProjection (READ MODEL)
├── productId: UUID               (PK — one row per product per tenant)
├── tenantId: string
├── totalQuantity: int            (sum of all ACTIVE batch quantities)
├── availableQuantity: int        (totalQuantity — for MVP, equals totalQuantity since no reservations)
├── batchCount: int               (number of ACTIVE batches)
├── nextExpirationDate: LocalDate (soonest expiration among ACTIVE batches)
├── expiredQuantity: int          (quantity in EXPIRED batches)
├── valueAtCost: BigDecimal       (sum of batch quantities × costPerUnit)
├── status: StockStatus           (IN_STOCK | LOW_STOCK | OUT_OF_STOCK | OVERSTOCKED)
└── lastUpdatedAt: LocalDateTime  (when this projection was last refreshed)
```

#### StockStatus Enum

| Status | Condition |
|--------|-----------|
| `IN_STOCK` | totalQuantity > reorderPoint (or reorderPoint not set) |
| `LOW_STOCK` | 0 < totalQuantity ≤ reorderPoint |
| `OUT_OF_STOCK` | totalQuantity = 0 |
| `OVERSTOCKED` | totalQuantity > maxStock (if maxStock is set) |

#### Update Strategy

StockProjection is updated by consuming domain events:
- `BatchReceived` → increment totalQuantity, recalculate status
- `MovementRecorded` → apply quantity delta, recalculate status
- `BatchExpired` → recalculate totalQuantity - expiredQuantity, update expiredQuantity
- `BatchDepleted` → check if all batches for product are depleted
- `BatchQuarantined` → recalculate availableQuantity

**Safety net:** The projection can be rebuilt entirely from Batches table on application startup. A `@PostConstruct` method recomputes the projection from scratch.

**🔮 Future enhancement (post-MVP):** Scheduled reconciliation job (e.g., nightly cron that verifies projection accuracy against current batch data and logs discrepancies). Startup rebuild is sufficient for MVP production volumes.

### 2.4 Location (Simple Entity for MVP)

```
Location
├── locationId: UUID          (PK, generated)
├── tenantId: string          (NOT optional)
├── name: string              (e.g. "Shelf A-3", "Fridge 2")
├── type: LocationType        (SHELF | FRIDGE | CONTROLLED | QUARANTINE)
├── isActive: boolean
├── createdAt: timestamp
└── updatedAt: timestamp
```

**Design decision:** Location is a persisted entity (own table, own repository), not an inline string. Rationale: pharmacies need to define their storage layout, and batch location management requires referential integrity. The FEFO engine may also consider location type (e.g., fridge vs. shelf) for allocation in future phases.

---

## 3. Invariants & Business Rules

| Rule ID | Rule | Enforcement Layer | Error Code |
|---------|------|-------------------|------------|
| INV-R01 | expirationDate MUST be in the future at receipt time | Domain validation | EXPIRATION_IN_PAST |
| INV-R02 | currentQuantity MUST NEVER be negative | Domain invariant on every OUT movement | NEGATIVE_QUANTITY |
| INV-R03 | currentQuantity MUST be ≤ initialQuantity | DB CHECK constraint | QUANTITY_EXCEEDS_INITIAL |
| INV-R04 | lotNumber + productId + expirationDate is unique per tenant | DB UNIQUE constraint | DUPLICATE_BATCH |
| INV-R05 | EXPIRED batches MUST NOT be picked | FEFO query filter | BATCH_EXPIRED |
| INV-R06 | NEAR_EXPIRY status is calculated from threshold, never set manually | Domain service | — |
| INV-R07 | DEPLETED batch auto-transitions when currentQuantity = 0 | Domain invariant check | — |
| INV-R08 | EXPIRED status auto-set when expirationDate < today | Scheduled job | — |
| INV-R09 | WRITTEN_OFF batches MUST NOT be reactivated | Status transition validation | BATCH_WRITTEN_OFF |
| INV-R10 | QUARANTINED batches MUST NOT be picked | FEFO query filter | BATCH_QUARANTINED |
| INV-R11 | initialQuantity MUST be > 0 at receipt | Jakarta Validation `@Positive` | INVALID_QUANTITY |
| INV-R12 | costPerUnit MUST be > 0 at receipt | Jakarta Validation `@Positive` | INVALID_COST |
| INV-R13 | Movements are immutable — no UPDATE or DELETE | DB trigger or app user privileges | MOVEMENT_IMMUTABLE |
| INV-R14 | Every OUT movement MUST reference a batchId | Domain validation | BATCH_REQUIRED |
| INV-R15 | Movement quantity is always positive; direction indicates sign | Domain invariant | INVALID_DIRECTION |
| INV-R16 | ADJUSTMENT movement requires reason | Jakarta Validation `@NotBlank` | REASON_REQUIRED |
| INV-R17 | performedAt MUST be ≤ recordedAt | Domain validation | INVALID_TIMESTAMP |
| INV-R18 | A RECEIPT movement MUST create the associated Batch in same transaction | Transactional boundary | — |
| INV-R19 | Location name MUST be unique per tenant | DB UNIQUE constraint | DUPLICATE_LOCATION |
| INV-R20 | A batch receiving for a deactivated product SHALL be rejected | Application validation | PRODUCT_DEACTIVATED |

---

## 4. REST API Specification

Base path: `/api/v1/inventory`

### 4.1 Batches API

| Method | Path | Description | Auth Required |
|--------|------|-------------|---------------|
| POST | `/api/v1/inventory/batches/receive` | Receive stock (creates batch + receipt movement) | JWT |
| GET | `/api/v1/inventory/batches` | List batches (filterable) | JWT |
| GET | `/api/v1/inventory/batches/{id}` | Get batch detail | JWT |
| POST | `/api/v1/inventory/batches/{id}/quarantine` | Quarantine a batch | JWT (manager) |
| POST | `/api/v1/inventory/batches/{id}/write-off` | Write off a batch | JWT (manager) |

#### POST /api/v1/inventory/batches/receive — Request

```json
{
  "productId": "uuid",              // required
  "lotNumber": "L2405A",            // required, max 100
  "manufacturingDate": "2024-05-15", // optional
  "expirationDate": "2026-12-15",   // required, MUST be future
  "quantity": 500,                  // required, > 0
  "locationId": "uuid",             // required, FK to locations
  "costPerUnit": 12.50,             // required, > 0
  "notes": "Received from Lab Genérico"  // optional
}
```

#### POST /api/v1/inventory/batches/receive — Response (201 Created)

```json
{
  "id": "uuid",
  "productId": "uuid",
  "productName": "Ibuprofeno 400mg",
  "lotNumber": "L2405A",
  "manufacturingDate": "2024-05-15",
  "expirationDate": "2026-12-15",
  "initialQuantity": 500,
  "currentQuantity": 500,
  "locationId": "uuid",
  "locationName": "Shelf A-3",
  "status": "ACTIVE",
  "costPerUnit": 12.50,
  "receivedDate": "2026-06-18T10:30:00Z",
  "notes": "Received from Lab Genérico",
  "version": 0
}
```

#### POST /api/v1/inventory/batches/{id}/quarantine — Request

```json
{
  "reason": "Suspected temperature excursion during transport",
  "notes": "Batch isolated pending quality review"
}
```

#### POST /api/v1/inventory/batches/{id}/quarantine — Response (200 OK)

```json
{
  "id": "uuid",
  "status": "QUARANTINED",
  "reason": "Suspected temperature excursion during transport",
  "version": 1
}
```

#### POST /api/v1/inventory/batches/{id}/write-off — Request

```json
{
  "reason": "Expired and disposed per SOP-42",
  "notes": "Disposal certificate #DC-2026-001 attached"
}
```

#### POST /api/v1/inventory/batches/{id}/write-off — Response (200 OK)

```json
{
  "id": "uuid",
  "status": "WRITTEN_OFF",
  "reason": "Expired and disposed per SOP-42",
  "version": 2
}
```

### 4.2 Movements API

| Method | Path | Description | Auth Required |
|--------|------|-------------|---------------|
| GET | `/api/v1/inventory/movements` | List movements (filterable, paginated) | JWT |
| GET | `/api/v1/inventory/movements/{id}` | Get movement detail | JWT |
| POST | `/api/v1/inventory/movements/adjust` | Record an inventory adjustment | JWT (manager) |

#### POST /api/v1/inventory/movements/adjust — Request

```json
{
  "productId": "uuid",          // required
  "batchId": "uuid",            // optional — null for product-level adjustment
  "quantity": 5,                // required, > 0
  "direction": "IN",            // IN = increase, OUT = decrease
  "reason": "Cycle count correction — found 5 extra units",  // required
  "performedAt": "2026-06-18T14:00:00Z",     // required
  "referenceType": "CYCLE_COUNT",
  "referenceId": "uuid",
  "notes": "Adjusted after physical count discrepancy"
}
```

#### POST /api/v1/inventory/movements/adjust — Response (201 Created)

```json
{
  "id": "uuid",
  "movementType": "ADJUSTMENT",
  "direction": "IN",
  "quantity": 5,
  "productId": "uuid",
  "batchId": "uuid",
  "reason": "Cycle count correction — found 5 extra units",
  "performedBy": "user-123",
  "performedAt": "2026-06-18T14:00:00Z",
  "recordedAt": "2026-06-18T14:05:00Z",
  "source": "API"
}
```

### 4.3 Stock API

| Method | Path | Description | Auth Required |
|--------|------|-------------|---------------|
| GET | `/api/v1/inventory/stock/summary` | Aggregated stock summary | JWT |
| GET | `/api/v1/inventory/stock/products` | Stock positions for ALL products | JWT |
| GET | `/api/v1/inventory/stock/products/{productId}` | Stock position for a single product | JWT |
| GET | `/api/v1/inventory/products/{productId}/available-batches` | List pickable batches for FEFO | JWT |

#### GET /api/v1/inventory/stock/summary — Response (200 OK)

```json
{
  "totalProducts": 150,
  "totalStockValue": 1250000.00,
  "lowStockCount": 12,
  "outOfStockCount": 3,
  "nearExpiryCount": 8,
  "expiredCount": 1
}
```

#### GET /api/v1/inventory/stock/products — Response (200 OK)

Supports `?productIds=id1,id2,id3` for batch hydration (used by product cards).

```json
[
  {
    "productId": "uuid",
    "productName": "Ibuprofeno 400mg",
    "totalQuantity": 150,
    "availableQuantity": 150,
    "nextExpirationDate": "2026-12-15",
    "batchCount": 3,
    "valueAtCost": 1875.00,
    "status": "IN_STOCK"
  }
]
```

#### GET /api/v1/inventory/products/{productId}/available-batches — Response (200 OK)

Returns batches sorted by FEFO order for the Sales context to preview.

```json
{
  "productId": "uuid",
  "productName": "Ibuprofeno 400mg",
  "requestedQuantity": 100,
  "totalAvailable": 150,
  "allocations": [
    {
      "batchId": "uuid",
      "lotNumber": "L2405A",
      "expirationDate": "2026-08-15",
      "currentQuantity": 80,
      "allocatedQuantity": 80
    },
    {
      "batchId": "uuid",
      "lotNumber": "L2406B",
      "expirationDate": "2026-12-15",
      "currentQuantity": 120,
      "allocatedQuantity": 20
    }
  ]
}
```

### 4.4 Locations API

| Method | Path | Description | Auth Required |
|--------|------|-------------|---------------|
| GET | `/api/v1/inventory/locations` | List active locations | JWT |
| POST | `/api/v1/inventory/locations` | Create location | JWT (manager) |
| PUT | `/api/v1/inventory/locations/{id}` | Update location | JWT (manager) |
| DELETE | `/api/v1/inventory/locations/{id}` | Soft-deactivate location | JWT (manager) |

---

## 5. Query Specifications

### 5.1 GET /api/v1/inventory/batches — Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `productId` | UUID | No | — | Filter by product |
| `status` | string | No | — | Comma-separated BatchStatus values (e.g. `ACTIVE,NEAR_EXPIRY`) |
| `expirationDateFrom` | date | No | — | Start of expiry range |
| `expirationDateTo` | date | No | — | End of expiry range |
| `search` | string | No | — | Partial match on lot number |
| `page` | int | No | `0` | Zero-indexed page number |
| `size` | int | No | `20` | Page size (max 100) |
| `sort` | string | No | `receivedDate:desc` | Sort: `receivedDate`, `expirationDate`, `currentQuantity`, `status` |

### 5.2 GET /api/v1/inventory/movements — Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `productId` | UUID | No | — | Filter by product |
| `batchId` | UUID | No | — | Filter by batch |
| `movementType` | string | No | — | Filter by MovementType |
| `dateFrom` | datetime | No | — | Start of performedAt range |
| `dateTo` | datetime | No | — | End of performedAt range |
| `page` | int | No | `0` | Zero-indexed page number |
| `size` | int | No | `20` | Page size (max 100) |
| `sort` | string | No | `performedAt:desc` | Sort: `performedAt`, `recordedAt`, `movementType` |

### 5.3 GET /api/v1/inventory/stock/products — Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `productIds` | string | No | — | Comma-separated UUIDs for batch hydration |
| `status` | string | No | — | Filter by StockStatus |
| `categoryId` | UUID | No | — | Filter by product category (join with product_catalog) |
| `search` | string | No | — | Search by product name or SKU (join) |
| `page` | int | No | `0` | Zero-indexed page number |
| `size` | int | No | `20` | Page size (max 100) |
| `sort` | string | No | `productName:asc` | Sort: `productName`, `totalQuantity`, `nextExpirationDate` |

---

## 6. Validation Rules

### 6.1 Batch Receiving Validation

| Field | Rule | Error Code | HTTP Status |
|-------|------|-----------|-------------|
| `productId` | REQUIRED, MUST reference active product | `PRODUCT_NOT_FOUND` | 404 |
| `productId` | Product MUST be active (not deactivated) | `PRODUCT_DEACTIVATED` | 409 |
| `lotNumber` | REQUIRED, max 100 chars | `INVALID_LOT_NUMBER` | 400 |
| `expirationDate` | REQUIRED, MUST be in the future | `EXPIRATION_IN_PAST` | 400 |
| `quantity` | REQUIRED, MUST be > 0 | `INVALID_QUANTITY` | 400 |
| `locationId` | REQUIRED, MUST reference active location | `LOCATION_NOT_FOUND` | 404 |
| `costPerUnit` | REQUIRED, MUST be > 0 | `INVALID_COST` | 400 |
| `lotNumber + productId + expirationDate` | MUST be unique per tenant | `DUPLICATE_BATCH` | 409 |

### 6.2 Movement Validation

| Field | Rule | Error Code | HTTP Status |
|-------|------|-----------|-------------|
| `productId` | REQUIRED, MUST exist | `PRODUCT_NOT_FOUND` | 404 |
| `batchId` | REQUIRED for OUT movements | `BATCH_REQUIRED` | 400 |
| `batchId` | Batch MUST be ACTIVE for OUT movements (not EXPIRED/QUARANTINED) | `BATCH_NOT_AVAILABLE` | 409 |
| `quantity` | REQUIRED, MUST be > 0 | `INVALID_QUANTITY` | 400 |
| `quantity` | For OUT, MUST be ≤ batch.currentQuantity | `INSUFFICIENT_STOCK` | 409 |
| `reason` | REQUIRED for ADJUSTMENT and WRITE_OFF | `REASON_REQUIRED` | 400 |
| `performedAt` | REQUIRED, MUST be ≤ recordedAt | `INVALID_TIMESTAMP` | 400 |

### 6.3 Quarantine / Write-Off Validation

| Field | Rule | Error Code | HTTP Status |
|-------|------|-----------|-------------|
| `reason` | REQUIRED | `REASON_REQUIRED` | 400 |
| Batch status | Batch MUST NOT already be WRITTEN_OFF | `BATCH_WRITTEN_OFF` | 409 |
| Batch status | Batch MUST NOT already be EXPIRED (redundant, but allowed with info) | — | — |
| Batch existence | MUST reference existing batch | `BATCH_NOT_FOUND` | 404 |

---

## 7. Domain Events

All events are published synchronously within the same transaction via Spring `ApplicationEventPublisher` + `@TransactionalEventListener` (Phase 1 MVP strategy). Phase 2 will migrate to an outbox pattern + message broker for cross-context eventual consistency.

### 7.1 BatchReceived

```json
{
  "eventType": "BatchReceived",
  "batchId": "uuid",
  "productId": "uuid",
  "tenantId": "tenant-123",
  "lotNumber": "L2405A",
  "expirationDate": "2026-12-15",
  "quantity": 500,
  "locationId": "uuid",
  "costPerUnit": 12.50,
  "timestamp": "2026-06-18T10:30:00Z"
}
```

**Published when:** POST `/api/v1/inventory/batches/receive` succeeds (201 Created).
**Consumers:** Alerting (evaluate low stock), StockProjection (update totals), Reporting.

### 7.2 BatchExpired

```json
{
  "eventType": "BatchExpired",
  "batchId": "uuid",
  "productId": "uuid",
  "tenantId": "tenant-123",
  "lotNumber": "L2405A",
  "quantity": 80,
  "expirationDate": "2026-06-17",
  "timestamp": "2026-06-18T00:00:00Z"
}
```

**Published when:** Scheduled job detects `expirationDate < today` for ACTIVE/NEAR_EXPIRY batches.
**Consumers:** Alerting (create critical alert), StockProjection (update), Sales context (block future picks).

### 7.3 BatchNearExpiry

```json
{
  "eventType": "BatchNearExpiry",
  "batchId": "uuid",
  "productId": "uuid",
  "tenantId": "tenant-123",
  "lotNumber": "L2405A",
  "quantity": 80,
  "daysUntilExpiry": 45,
  "timestamp": "2026-06-18T00:00:00Z"
}
```

**Published when:** Daily scheduled check finds batch within the configurable warning window (default: 90 days).
**Consumers:** Alerting (create warning alert).

### 7.4 BatchDepleted

```json
{
  "eventType": "BatchDepleted",
  "batchId": "uuid",
  "productId": "uuid",
  "tenantId": "tenant-123",
  "lotNumber": "L2405A",
  "timestamp": "2026-06-18T14:30:00Z"
}
```

**Published when:** OUT movement reduces `currentQuantity` to 0.
**Consumers:** StockProjection (decrement batch count), Alerting (re-evaluate product status).

### 7.5 BatchQuarantined

```json
{
  "eventType": "BatchQuarantined",
  "batchId": "uuid",
  "productId": "uuid",
  "tenantId": "tenant-123",
  "lotNumber": "L2405A",
  "reason": "Suspected temperature excursion",
  "quantity": 80,
  "timestamp": "2026-06-18T15:00:00Z"
}
```

**Published when:** POST `/api/v1/inventory/batches/{id}/quarantine` succeeds.
**Consumers:** Alerting (create alert), StockProjection (reduce available quantity).

### 7.6 MovementRecorded

```json
{
  "eventType": "MovementRecorded",
  "movementId": "uuid",
  "movementType": "SALE",
  "direction": "OUT",
  "productId": "uuid",
  "batchId": "uuid",
  "quantity": 10,
  "tenantId": "tenant-123",
  "performedAt": "2026-06-18T14:30:00Z",
  "timestamp": "2026-06-18T14:30:05Z"
}
```

**Published when:** Any movement is created.
**Consumers:** StockProjection (update totals), Alerting (re-evaluate low stock).

### 7.7 StockDepleted

```json
{
  "eventType": "StockDepleted",
  "productId": "uuid",
  "tenantId": "tenant-123",
  "timestamp": "2026-06-18T14:30:00Z"
}
```

**Published when:** All batches for a product reach 0 quantity.
**Consumers:** Alerting (trigger LOW_STOCK alert), StockProjection (set status to OUT_OF_STOCK).

### 7.8 StockReplenished

```json
{
  "eventType": "StockReplenished",
  "productId": "uuid",
  "tenantId": "tenant-123",
  "newQuantity": 500,
  "timestamp": "2026-06-19T09:00:00Z"
}
```

**Published when:** Stock goes from 0 to > 0 (on receipt).
**Consumers:** Alerting (resolve LOW_STOCK alert).

---

## 8. FEFO Allocation Engine

### 8.1 Architecture

The FEFO engine is structured as three distinct components following the hexagonal architecture:

```
┌─────────────────────────────────────────────────────────────┐
│                    FEFO ALLOCATION ENGINE                     │
│                                                              │
│  Domain Layer:                                               │
│    AllocationStrategy (INTERFACE)                            │
│    FefoAllocationStrategy (IMPLEMENTS AllocationStrategy)    │
│    BatchAllocation (VALUE OBJECT)                            │
│    InsufficientStockException (DOMAIN EXCEPTION)             │
│                                                              │
│  Application Layer:                                          │
│    BatchAllocator (ORCHESTRATOR — @Transactional)            │
│                                                              │
│  Boundary:                                                    │
│    AllocationStrategy port ← FefoAllocationStrategy adapter  │
│    BatchRepository port ← JpaBatchRepository adapter         │
│    MovementRepository port ← JpaMovementRepository adapter   │
└─────────────────────────────────────────────────────────────┘
```

### 8.2 AllocationStrategy (Interface)

```java
public interface AllocationStrategy {
    List<BatchAllocation> allocate(ProductId productId, int requestedQuantity);
}
```

### 8.3 FefoAllocationStrategy (Implementation)

**Algorithm:**

1. Query all ACTIVE and NEAR_EXPIRY batches for the product where `expirationDate > today AND currentQuantity > 0`
2. Sort by `expirationDate ASC, receivedDate ASC` (FEFO primary, FIFO tiebreaker)
3. Iterate: allocate from earliest-expiring batch first
4. If total available quantity across all eligible batches < requestedQuantity → throw `InsufficientStockException`
5. For each batch allocation, record: `batchId`, `allocatedQuantity`, `expirationDate`
6. Return `List<BatchAllocation>`

### 8.4 BatchAllocation (Value Object)

```java
public record BatchAllocation(
    BatchId batchId,
    int allocatedQuantity,
    LocalDate expirationDate,
    BigDecimal costPerUnit
) {}
```

### 8.5 BatchAllocator (Application Service)

```java
public class BatchAllocator {
    private final AllocationStrategy strategy;
    private final BatchRepository batchRepository;
    private final MovementRepository movementRepository;

    @Transactional
    public AllocationResult allocate(ProductId productId, int requestedQuantity) {
        // 1. Strategy determines allocation plan (read-only)
        List<BatchAllocation> allocations = strategy.allocate(productId, requestedQuantity);

        // 2. Execute: update each batch with optimistic locking
        //    Retry up to 3 times on ObjectOptimisticLockingFailureException
        for (BatchAllocation alloc : allocations) {
            Batch batch = batchRepository.findById(alloc.batchId())
                .orElseThrow(() -> new BatchNotFoundException(alloc.batchId()));

            batch.decrementQuantity(alloc.allocatedQuantity());
            batchRepository.save(batch);

            Movement movement = Movement.createSaleMovement(
                batch.productId(),
                batch.batchId(),
                alloc.allocatedQuantity(),
                alloc.costPerUnit()
            );
            movementRepository.save(movement);
        }

        return new AllocationResult(allocations);
    }
}
```

### 8.6 Edge Cases

| Edge Case | Resolution |
|-----------|-----------|
| Single batch fully covers request | Allocate all from that batch |
| Multiple batches needed | Partial from each, earliest-expiry first |
| Insufficient total stock | `InsufficientStockException` — sale rejected |
| Batch expires between read and write | Retry with fresh query — batch excluded |
| Concurrent pick on same batch | Optimistic lock → retry up to 3 times |
| Same expiry date, different received dates | FIFO: `receivedDate ASC` tiebreaker |
| All quantity from one batch, leaving remainder | Remaining stays available for next pick |
| Batch quarantined between read and write | Retry with fresh query — batch excluded |

---

## 9. Batch Status Transitions

### State Machine Diagram

```
                 ┌──────────┐
                 │ RECEIVED │  (initial state after receipt)
                 └────┬─────┘
                      │  (auto-transition on first commit)
                 ┌────▼─────┐
          ┌──────│  ACTIVE  │◄────── (reactivation — post-MVP)
          │      └────┬─────┘
          │           │
          │      ┌────▼──────┐
          │      │NEAR_EXPIRY│  (auto, based on threshold days)
          │      └────┬──────┘
          │           │
          │      ┌────▼────┐
          ├──────│ EXPIRED │  (auto, expirationDate passed)
          │      └────┬────┘
          │           │
     ┌────▼─────┐    │
     │QUARANTINED│    │
     └────┬─────┘    │
          │          │
     ┌────▼──────────▼──────┐
     │     WRITTEN_OFF      │  (manual, with documentation)
     └──────────────────────┘

     DEPLETED — reached from ACTIVE or NEAR_EXPIRY when currentQuantity = 0
```

### Transition Rules

| From | To | Trigger | Valid |
|------|----|---------|-------|
| RECEIVED | ACTIVE | Auto on batch creation (default status) | ✅ |
| ACTIVE | NEAR_EXPIRY | Scheduled check — expirationDate within warning window | ✅ |
| ACTIVE | QUARANTINED | Manual — POST /batches/{id}/quarantine | ✅ |
| ACTIVE | DEPLETED | OUT movement reduces currentQuantity to 0 | ✅ |
| ACTIVE | WRITTEN_OFF | Manual — POST /batches/{id}/write-off | ✅ |
| NEAR_EXPIRY | EXPIRED | Scheduled check — expirationDate < today | ✅ |
| NEAR_EXPIRY | QUARANTINED | Manual | ✅ |
| NEAR_EXPIRY | DEPLETED | OUT movement reduces to 0 | ✅ |
| NEAR_EXPIRY | WRITTEN_OFF | Manual | ✅ |
| EXPIRED | QUARANTINED | Already implied by EXPIRED (redundant, allowed) | ✅ |
| EXPIRED | WRITTEN_OFF | Manual | ✅ |
| QUARANTINED | WRITTEN_OFF | Manual | ✅ |
| DEPLETED | *(any)* | Terminal state | ❌ |
| WRITTEN_OFF | *(any)* | Terminal state | ❌ |
| EXPIRED | ACTIVE | Cannot reactivate expired batch | ❌ |
| QUARANTINED | ACTIVE | Post-MVP (quality validation) | ❌ |

---

## 10. Concurrency Strategy

### 10.1 Optimistic Locking

- Every Batch has a `version` column (JPA `@Version` annotation).
- FEFO allocation runs inside a `@Transactional` method (`BatchAllocator.allocate`).
- The allocation reads batches, plans allocations, then executes updates.
- When updating batch quantities, JPA checks `WHERE version = :expectedVersion`.
- If another transaction already modified the same batch, `ObjectOptimisticLockingFailureException` is thrown.

### 10.2 Retry Mechanism

- Layer: `BatchAllocator` or a `@Retryable` wrapper in the application service.
- On `ObjectOptimisticLockingFailureException`: retry up to **3 times** with exponential backoff (100ms, 200ms, 400ms).
- Each retry re-reads the allocation plan from scratch (fresh data).
- After 3 retries, throw `ConcurrentPickException` to the caller.
- The caller (Sales context) SHALL surface the error to the user and ask them to retry.

### 10.3 Query-Level Locking

- The FEFO query uses `SELECT ... FOR UPDATE` to lock batch rows during the transaction (pessimistic read lock on the selected rows).
- This prevents phantom reads: if two transactions run the FEFO query simultaneously, the second waits for the first to complete.
- **Tradeoff:** `FOR UPDATE` reduces concurrency. Acceptable for pharmacy volumes (hundreds of picks/day, not thousands/second). If contention becomes a problem in the future, switch to `SKIP LOCKED` (PostgreSQL 9.5+) to skip locked rows and allocate from the next available batch.

### 10.4 Index Strategy for FEFO

```sql
-- Primary FEFO pick query index
CREATE INDEX idx_batches_fefo_pick
    ON inventory.batches (tenant_id, product_id, status, expiration_date, received_date)
    WHERE status IN ('ACTIVE', 'NEAR_EXPIRY') AND current_quantity > 0;

-- Expiry management index
CREATE INDEX idx_batches_expiry_date
    ON inventory.batches (expiration_date)
    WHERE status IN ('ACTIVE', 'NEAR_EXPIRY');
```

### 10.5 Concurrency Scenario: Two Concurrent Picks

```
Time  Transaction A                     Transaction B
────  ────────────────────────────────  ────────────────────────────────
T1    BEGIN                             BEGIN
T2    SELECT batches FOR UPDATE         (WAITING — row locked)
T3    Allocate 10 from batch-X
T4    UPDATE batch SET qty=qty-10,
          version=version+1
       WHERE version=0
T5    COMMIT                            (lock released)
T6                                       SELECT batches FOR UPDATE
T7                                       Allocate from batch-X
                                          (qty now reflects A's deduction)
T8                                       UPDATE batch (version=1)
T9                                       COMMIT
```

Result: No oversell. Both transactions see correct quantities.

### 10.6 Concurrency Scenario: Retry on Version Conflict

```
Time  Transaction A                     Transaction B
────  ────────────────────────────────  ────────────────────────────────
T1    BEGIN (no FOR UPDATE — read-only) BEGIN (no FOR UPDATE — read-only)
T2    SELECT batches (v=0, qty=50)      SELECT batches (v=0, qty=50)
T3    Allocate 30 from batch-X          Allocate 40 from batch-X
T4    UPDATE batch SET qty=20,          (WAITING — write lock on row)
          version=1
       WHERE version=0
T5    COMMIT                            ➜ UPDATE batch SET qty=10,
                                          version=1
                                        WHERE version=0
                                         → 0 rows affected!
T6                                       ROLLBACK
T7                                       RETRY #1: re-read (qty=20)
T8                                       Allocate 40 → INSUFFICIENT
T9                                       Carry to next batch...
```

---

## 11. Key Scenarios

### 11.1 Batch Receiving

#### Scenario 1: Receive stock successfully

```
GIVEN: Product "Ibuprofeno 400mg" exists and is ACTIVE
  AND: Location "Shelf A-3" exists and is ACTIVE
  AND: Authenticated user with tenant "pharmacy-123"
WHEN:  User POSTs to /api/v1/inventory/batches/receive with:
       {
         "productId": "prod-uuid",
         "lotNumber": "L2405A",
         "expirationDate": "2026-12-15",
         "quantity": 500,
         "locationId": "loc-uuid",
         "costPerUnit": 12.50
       }
THEN:  Response is 201 Created
  AND: A Batch is created with status ACTIVE, currentQuantity = 500
  AND: A RECEIPT movement is created (direction IN, quantity 500)
  AND: BatchReceived event is published
  AND: StockProjection for the product is updated (+500 quantity, +1 batch count)
```

#### Scenario 2: Receive with past expiration date

```
GIVEN: Authenticated user with valid tenant
WHEN:  User POSTs to /api/v1/inventory/batches/receive with expirationDate = "2020-01-01"
THEN:  Response is 400 Bad Request
  AND: Error code is EXPIRATION_IN_PAST
  AND: No batch or movement is created
```

#### Scenario 3: Receive with negative quantity

```
GIVEN: Authenticated user with valid tenant
WHEN:  User POSTs to /api/v1/inventory/batches/receive with quantity = -5
THEN:  Response is 400 Bad Request
  AND: Error code is INVALID_QUANTITY
  AND: No batch or movement is created
```

#### Scenario 4: Receive for non-existent product

```
GIVEN: Product ID does not exist
WHEN:  User POSTs to /api/v1/inventory/batches/receive with that productId
THEN:  Response is 404 Not Found
  AND: Error code is PRODUCT_NOT_FOUND
```

#### Scenario 5: Receive for deactivated product

```
GIVEN: Product exists but is DEACTIVATED (isActive = false)
WHEN:  User POSTs to /api/v1/inventory/batches/receive with that productId
THEN:  Response is 409 Conflict
  AND: Error code is PRODUCT_DEACTIVATED
```

#### Scenario 6: Duplicate batch (same lot + product + expiry)

```
GIVEN: Batch exists for product P1, lot "L2405A", exp "2026-12-15"
WHEN:  User POSTs /api/v1/inventory/batches/receive with same productId, lotNumber, expirationDate
THEN:  Response is 409 Conflict
  AND: Error code is DUPLICATE_BATCH
  AND: Message indicates the unique constraint violation
```

#### Scenario 7: Same lot, same product, different expiry — allowed

```
GIVEN: Batch exists for product P1, lot "L2405A", exp "2026-12-15"
WHEN:  User POSTs /api/v1/inventory/batches/receive with same productId, lot "L2405A"
       but different expirationDate "2027-06-15"
THEN:  Response is 201 Created
  AND: A new batch is created (different expiry = different business key)
```

### 11.2 FEFO Picking

#### Scenario 8: FEFO picks from soonest-expiring batch

```
GIVEN: Product "Ibuprofeno 400mg" has 3 ACTIVE batches:
       | Batch | Expiration | Qty |
       | B1    | 2026-08-15 | 100 |
       | B2    | 2026-12-15 | 100 |
       | B3    | 2027-03-20 | 100 |
WHEN:  FefoAllocationStrategy.allocate(productId, 50)
THEN:  Returns [BatchAllocation(B1, 50)]
  AND: B1 is selected because it expires first
```

#### Scenario 9: FEFO splits across multiple batches

```
GIVEN: Batches as above (B1=100, B2=100, B3=100)
WHEN:  FefoAllocationStrategy.allocate(productId, 150)
THEN:  Returns [
         BatchAllocation(B1, 100),  ← fully depleted
         BatchAllocation(B2, 50)    ← partially allocated
       ]
  AND: B3 is not touched
```

#### Scenario 10: FEFO with insufficient stock

```
GIVEN: Batches as above (total = 300)
WHEN:  FefoAllocationStrategy.allocate(productId, 500)
THEN:  Throws InsufficientStockException
  AND: Message includes "Requested 500 but only 300 available"
```

#### Scenario 11: FEFO excludes expired batches

```
GIVEN: Product has 3 batches:
       | Batch | Expiry    | Status  | Qty |
       | B1    | 2026-01-01| EXPIRED | 50  |
       | B2    | 2026-08-15| ACTIVE  | 100 |
       | B3    | 2026-12-15| ACTIVE  | 100 |
WHEN:  FefoAllocationStrategy.allocate(productId, 50)
THEN:  Returns [BatchAllocation(B2, 50)]  ← B1 is excluded
  AND: B2 is selected as the soonest-expiring ACTIVE batch
```

#### Scenario 12: FEFO includes NEAR_EXPIRY batches

```
GIVEN: Product has batches B1 (NEAR_EXPIRY, exp in 30d) and B2 (ACTIVE, exp in 200d)
WHEN:  FefoAllocationStrategy.allocate(productId, 10)
THEN:  Returns [BatchAllocation(B1, 10)]
  AND: B1 is selected because NEAR_EXPIRY batches ARE eligible for picking
```

#### Scenario 13: FEFO same expiry — FIFO tiebreaker

```
GIVEN: Product has batches:
       | Batch | Expiry    | Received   | Qty |
       | B1    | 2026-12-15| 2026-01-10 | 100 |
       | B2    | 2026-12-15| 2026-06-01 | 100 |
WHEN:  FefoAllocationStrategy.allocate(productId, 50)
THEN:  Returns [BatchAllocation(B1, 50)]
  AND: B1 is selected because it was received first (same expiry, FIFO tiebreaker)
```

#### Scenario 14: Concurrent FEFO picks — optimistic lock success

```
GIVEN: Product has batch B1 with qty=100, version=0
WHEN:  Two concurrent requests allocate from B1 simultaneously
THEN:  First request succeeds (version 0→1, qty 100→60 for alloc of 40)
  AND: Second request retries (version conflict), re-reads (qty=60), and succeeds
  AND: No oversell — total allocated ≤ 100
```

### 11.3 Batch Lifecycle

#### Scenario 15: Batch auto-expires via scheduled job

```
GIVEN: Batch B1 exists with status ACTIVE, expirationDate = yesterday
WHEN:  The daily expiry check job runs
THEN:  Batch B1 status transitions to EXPIRED
  AND: BatchExpired event is published
  AND: StockProjection.expiredQuantity is incremented
  AND: StockProjection.totalQuantity is decremented
```

#### Scenario 16: Batch transitions to NEAR_EXPIRY

```
GIVEN: Batch B1 exists with status ACTIVE, expirationDate = 85 days from now
  AND: The near-expiry threshold is 90 days (tenant config)
WHEN:  The daily near-expiry check job runs
THEN:  Batch B1 status transitions to NEAR_EXPIRY
  AND: BatchNearExpiry event is published with daysUntilExpiry = 85
```

#### Scenario 17: Batch manually quarantined

```
GIVEN: Batch B1 exists with status ACTIVE, currentQuantity = 80
WHEN:  Manager POSTs /api/v1/inventory/batches/{id}/quarantine with reason
THEN:  Batch status transitions to QUARANTINED
  AND: BatchQuarantined event is published
  AND: Batch is excluded from future FEFO picks
  AND: No movement is created (status change only — quantity stays in QUARANTINED)
```

#### Scenario 18: Batch written off

```
GIVEN: Batch B1 exists with status EXPIRED, currentQuantity = 50
WHEN:  Manager POSTs /api/v1/inventory/batches/{id}/write-off with reason
THEN:  Batch status transitions to WRITTEN_OFF
  AND: A WRITE_OFF movement is created (direction OUT, quantity 50, batchId = B1)
  AND: StockProjection is updated (quantity decremented, expired quantity decremented)
```

#### Scenario 19: Write-off already written-off batch — rejected

```
GIVEN: Batch B1 exists with status WRITTEN_OFF
WHEN:  Manager POSTs /api/v1/inventory/batches/{id}/write-off
THEN:  Response is 409 Conflict
  AND: Error code is BATCH_WRITTEN_OFF
  AND: Message indicates batch cannot be written off twice
```

#### Scenario 20: Batch auto-depleted on last unit sold

```
GIVEN: Batch B1 exists with status ACTIVE, currentQuantity = 5
WHEN:  A SALE movement deducts all 5 units from B1
THEN:  Batch B1 currentQuantity = 0
  AND: Batch B1 status transitions to DEPLETED
  AND: BatchDepleted event is published
```

### 11.4 Stock Projection

#### Scenario 21: Stock projection updates after receipt

```
GIVEN: Product P1 has no stock (StockProjection.status = OUT_OF_STOCK, totalQuantity = 0)
WHEN:  BatchReceived event is consumed for P1 (500 units added)
THEN:  StockProjection.totalQuantity = 500
  AND: StockProjection.batchCount = 1
  AND: StockProjection.nextExpirationDate = the received batch's expiry
  AND: StockProjection.status = IN_STOCK (if reorderPoint ≤ 500)
```

#### Scenario 22: Stock projection shows LOW_STOCK

```
GIVEN: Product P1 has reorderPoint = 20
  AND: StockProjection.totalQuantity = 15
WHEN:  MovementRecorded event is consumed (OUT movement reducing from 20 to 15)
THEN:  StockProjection.totalQuantity = 15
  AND: StockProjection.status = LOW_STOCK
  AND: StockDepleted event is NOT published (quantity > 0)
```

#### Scenario 23: Stock projection shows OUT_OF_STOCK

```
GIVEN: Product P1 has totalQuantity = 5
WHEN:  MovementRecorded event consumes a SALE OUT of 5 units
THEN:  StockProjection.totalQuantity = 0
  AND: StockProjection.status = OUT_OF_STOCK
  AND: StockDepleted event is published
```

#### Scenario 24: Stock projection rebuild from batches on startup

```
GIVEN: StockProjection table has incorrect data (drift)
WHEN:  Application starts and the rebuild job runs
THEN:  StockProjection is recomputed from the batch table:
       SELECT productId, SUM(currentQuantity), COUNT(*), MIN(expirationDate)
       FROM inventory.batches WHERE status IN ('ACTIVE', 'NEAR_EXPIRY')
       GROUP BY productId
  AND: StockProjection matches the batch-level source of truth
```

### 11.5 Movements

#### Scenario 25: Movement immutability — cannot be updated

```
GIVEN: Movement M1 exists (RECEIPT, direction IN, quantity 500)
WHEN:  User attempts UPDATE on inventory.movements table directly
THEN:  Database rejects the operation (app user lacks UPDATE privilege)
  AND: Application provides no UPDATE endpoint for movements
```

#### Scenario 26: Adjustment requires reason

```
GIVEN: Authenticated user with manager role
WHEN:  User POSTs /api/v1/inventory/movements/adjust with:
       { "productId": "...", "quantity": 5, "direction": "IN" }
       (reason field missing)
THEN:  Response is 400 Bad Request
  AND: Error code is REASON_REQUIRED
```

#### Scenario 27: Inventory adjustment increases stock correctly

```
GIVEN: Product P1 has StockProjection.totalQuantity = 100
WHEN:  Manager POSTs /api/v1/inventory/movements/adjust with:
       { "productId": "P1", "quantity": 5, "direction": "IN",
         "reason": "Cycle count correction — found 5 extra units" }
THEN:  ADJUSTMENT movement is created (direction IN, quantity 5)
  AND: StockProjection.totalQuantity = 105
  AND: MovementRecorded event is published
```

#### Scenario 28: Inventory adjustment decreases stock below zero — rejected

```
GIVEN: Batch B1 has currentQuantity = 3
WHEN:  Manager POSTs /api/v1/inventory/movements/adjust with:
       { "productId": "P1", "batchId": "B1", "quantity": 10, "direction": "OUT",
         "reason": "..." }
THEN:  Response is 409 Conflict
  AND: Error code is INSUFFICIENT_STOCK
  AND: No movement or batch update occurs
```

### 11.6 Locations

#### Scenario 29: Create location successfully

```
GIVEN: Authenticated user with manager role
WHEN:  User POSTs to /api/v1/inventory/locations with:
       { "name": "Fridge 2", "type": "FRIDGE" }
THEN:  Response is 201 Created
  AND: Location is created with the given attributes
```

#### Scenario 30: Duplicate location name (same tenant)

```
GIVEN: Location "Shelf A-3" exists for tenant "pharmacy-123"
WHEN:  User from tenant "pharmacy-123" POSTs a new location named "Shelf A-3"
THEN:  Response is 409 Conflict
  AND: Error code is DUPLICATE_LOCATION
```

#### Scenario 31: Deactivate location with active batches — rejected

```
GIVEN: Location "Shelf A-3" has 5 ACTIVE batches referencing it
WHEN:  Manager DELETEs /api/v1/inventory/locations/{id}
THEN:  Response is 409 Conflict
  AND: Error code is LOCATION_HAS_BATCHES
```

### 11.7 Tenant Isolation

#### Scenario 32: Tenant isolation — cannot see other tenant's data

```
GIVEN: Tenant A has batch B1 (lot "L2405A") for product P1
  AND: Tenant B has batch B2 (lot "L2405A") for product P1
WHEN:  User from tenant A queries GET /api/v1/inventory/batches
THEN:  Only batch B1 is returned
  AND: Batch B2 is NOT in the response
```

#### Scenario 33: Tenant isolation — unique constraint is per-tenant

```
GIVEN: Tenant A has batch (product P1, lot "L2405A", exp "2026-12-15")
WHEN:  User from tenant B POSTs /api/v1/inventory/batches/receive with
       same product P1, lot "L2405A", exp "2026-12-15"
THEN:  Response is 201 Created (success)
  AND: Tenant B's batch is created — no conflict with tenant A's data
```

---

## 12. Frontend Specifications

### 12.1 Component Tree

```
components/inventory/
├── BatchReceivingForm.tsx       ← Form to receive stock
├── BatchList.tsx                ← Table/list of batches with filters
├── BatchDetail.tsx              ← Single batch view + movement history
├── StockCard.tsx                ← Summary card (dashboard)
├── StockStatusBadge.tsx         ← Color-coded badge
├── MovementTimeline.tsx         ← Chronological movement list
├── AdjustmentForm.tsx           ← Inventory adjustment form
└── ExpirationCalendar.tsx       ← Upcoming expiration grid
```

### 12.2 BatchReceivingForm

**Type:** Form component.

**Props:**
```typescript
interface BatchReceivingFormProps {
  products: ProductSummary[];
  locations: LocationSummary[];
  onSubmit: (data: BatchReceivingData) => Promise<void>;
  onCancel: () => void;
}

interface BatchReceivingData {
  productId: string;
  lotNumber: string;
  manufacturingDate?: string;
  expirationDate: string;
  quantity: number;
  locationId: string;
  costPerUnit: number;
  notes?: string;
}
```

**Rendering behavior:**
- Product search/select (autocomplete with SKU + name)
- Lot number text input
- Manufacturing date picker (optional)
- Expiration date picker (MUST be future date — inline validation)
- Quantity input (positive integer)
- Location dropdown
- Cost per unit (currency input)
- Notes textarea (optional)
- Submit + Cancel buttons

**States:**
- **Loading:** Skeleton form, product/location dropdowns disabled while fetching data
- **Pristine:** Empty form ready for input
- **Validating:** Inline validation on field blur: expiry must be future, quantity > 0, cost > 0
- **Submitting:** Spinner on submit button, all fields disabled
- **Success:** Toast + form reset (ready for next receipt) + optional "View batch" link
- **Error:** Server error banner: product deactivated, duplicate batch, product not found

### 12.3 BatchList

**Type:** Filterable data table.

**Props:**
```typescript
interface BatchListProps {
  productId?: string;    // Pre-filter to a specific product
  onBatchSelect: (batchId: string) => void;
}
```

**Rendering behavior:**
- Data table columns: Lot Number, Product, Expiration Date, Quantity, Status, Location, Received Date
- Color-coded status badges (green=ACTIVE, yellow=NEAR_EXPIRY, red=EXPIRED, orange=QUARANTINED, gray=DEPLETED, dark=WRITTEN_OFF)
- Filter bar: product search, status multi-select, expiry date range, lot number search
- Sortable columns
- Paginated (20 per page)

**States:**
- **Loading:** Skeleton rows (6-8 placeholder rows)
- **Empty:** "No batches found" illustration + "Receive your first stock" CTA button
- **Error:** Error message + Retry button
- **Data:** Rows with clickable batch IDs navigating to batch detail

### 12.4 BatchDetail

**Type:** Detail view.

**Props:**
```typescript
interface BatchDetailProps {
  batchId: string;
}
```

**Sections:**
- Header: Product name, Lot number, Status badge
- Attributes card: Expiration date, manufacturing date, received date, location
- Quantities card: Initial quantity, current quantity, percentage remaining (progress bar)
- Cost info: Cost per unit, total value
- Movement history (reuses MovementTimeline)
- Action bar: Quarantine button, Write-off button (manager role only)

**States:**
- **Loading:** Skeleton with header + card placeholders
- **Not found:** "Batch not found" illustration + back to batch list
- **Deactivated (EXPIRED/QUARANTINED/WRITTEN_OFF):** Warning banner at top with status explanation

### 12.5 StockCard

**Type:** Dashboard summary card.

**Props:**
```typescript
interface StockCardProps {
  title: string;
  value: number | string;
  subtitle?: string;
  icon: ReactNode;
  trend?: 'up' | 'down' | 'neutral';
  color: 'green' | 'yellow' | 'red' | 'orange' | 'blue';
}
```

**Usage in dashboard:**
- Total Products (value = count)
- Total Stock Value (value = formatted currency, e.g. "$1,250,000.00")
- Low Stock Items (value = count, color = yellow)
- Out of Stock Items (value = count, color = red)
- Near Expiry (value = count, color = orange)
- Expired Items (value = count, color = red)

**States:**
- **Loading:** Skeleton card with animated placeholder
- **Data:** Full card with icon, value, subtitle
- **Zero:** Shows "0" with neutral styling (not empty state)

### 12.6 StockStatusBadge

**Type:** Presentational badge.

**Props:**
```typescript
interface StockStatusBadgeProps {
  status: 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'OVERSTOCKED';
  size?: 'sm' | 'md' | 'lg';
}
```

**Color mapping:**
- `IN_STOCK` → green
- `LOW_STOCK` → yellow/amber
- `OUT_OF_STOCK` → red
- `OVERSTOCKED` → orange

### 12.7 MovementTimeline

**Type:** Chronological timeline component.

**Props:**
```typescript
interface MovementTimelineProps {
  productId?: string;
  batchId?: string;
  limit?: number;
}
```

**Rendering behavior:**
- Vertical timeline with dot + line for each movement
- Each entry: date/time, movement type icon, quantity (green for IN, red for OUT), user, reference
- Click on entry expands detail (movement ID, notes, source)
- "Show more" button when there are additional movements

**States:**
- **Loading:** Skeleton timeline (3-4 placeholder entries)
- **Empty:** "No movements recorded yet" + brief explanation
- **Error:** Error message + Retry

### 12.8 AdjustmentForm

**Type:** Form component.

**Props:**
```typescript
interface AdjustmentFormProps {
  products: ProductSummary[];
  onSubmit: (data: AdjustmentData) => Promise<void>;
  onCancel: () => void;
}

interface AdjustmentData {
  productId: string;
  batchId?: string;
  quantity: number;
  direction: 'IN' | 'OUT';
  reason: string;
  performedAt: string;
  referenceType?: string;
  referenceId?: string;
  notes?: string;
}
```

**States:**
- Same as BatchReceivingForm (Loading, Pristine, Validating, Submitting, Success, Error)
- Additional validation: reason is required, quantity > 0, OUT cannot exceed available stock

### 12.9 ExpirationCalendar

**Type:** Calendar/grid visualization.

**Props:**
```typescript
interface ExpirationCalendarProps {
  months?: number;   // How many months to show (default: 3)
  onBatchClick: (batchId: string) => void;
}
```

**Rendering behavior:**
- Monthly grid showing batches grouped by month of expiration
- Each cell: product name, lot number, quantity
- Color coding: green (3+ months away), yellow (1-3 months), red (< 1 month or expired)
- "Show more" within each month for overflow

---

## 13. Testing Requirements

Strict TDD: Domain tests MUST be written BEFORE domain implementation. Every requirement below corresponds to specific test classes.

### 13.1 Domain Unit Tests (JUnit 5)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `BatchTest` | Create batch with valid data; Create with past expiration → throws; Create with negative quantity → throws; Decrement quantity — success, insufficient stock → throws; Status transition matrix — all valid/invalid transitions; Duplicate lot+product+expiry detection (value equality); currentQuantity never negative invariant |
| `MovementTest` | Create movement — all types; No setters (immutability enforced); Direction validation (IN/OUT only); Reason required for ADJUSTMENT and WRITE_OFF; performedAt ≤ recordedAt invariant; OUT movement requires batchId |
| `FefoAllocationStrategyTest` | Picks soonest expiry first; Splits across multiple batches; Insufficient stock → InsufficientStockException; Excludes EXPIRED batches; Excludes QUARANTINED batches; Includes NEAR_EXPIRY batches; Same-expiry FIFO tiebreaker; Empty result when no eligible batches |
| `BatchAllocatorTest` | Allocation + movement creation; Retry on optimistic lock (3 attempts); Success on first attempt; Allocator orchestrates read → allocate → write correctly |
| `BatchStatusTest` | Full status transition matrix coverage — every valid/invalid pair tested; DEPLETED → any = rejected; WRITTEN_OFF → any = rejected; ACTIVE → DEPLETED on quantity=0; ACTIVE → NEAR_EXPIRY on threshold; ACTIVE → EXPIRED via scheduled job |
| `StockProjectionTest` | Computes totals from batch list; Detects LOW_STOCK (below reorderPoint); Detects OUT_OF_STOCK (all batches depleted); Detects OVERSTOCKED (above maxStock); Identifies nextExpirationDate from batch list; Handles empty batch list → status=OUT_OF_STOCK |

### 13.2 Application Tests (JUnit 5 + Mockito)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `ReceiveStockUseCaseTest` | Success — batch created, movement created, event published; Expired date rejected; Product not found; Duplicate batch (unique constraint); Deactivated product rejected |
| `RecordAdjustmentUseCaseTest` | Success — movement created, stock updated; Reason required (throws); Product not found; OUT adjustment exceeds available stock |
| `QuarantineBatchUseCaseTest` | Success — batch quarantined, event published; Already quarantined (idempotent — no error); Already written off → BATCH_WRITTEN_OFF; Batch not found |
| `WriteOffBatchUseCaseTest` | Success — batch written off, WRITE_OFF movement created; Already written off → rejected; Reason required; Batch not found; EXPIRED batch can be written off |
| `StockProjectionUpdaterTest` | Updates after BatchReceived; Updates after MovementRecorded; Updates after BatchExpired; Updates after BatchDepleted; Rebuild from scratch on startup |

### 13.3 Concurrency Tests (Critical)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `FefoConcurrencyTest` | 10 concurrent picks on same product — no oversell, total allocated ≤ total stock; 5 concurrent picks on same batch — all succeed or fail gracefully; Mix of IN and OUT movements — no race condition |
| `OptimisticLockRetryTest` | Version conflict → auto retry → succeed on attempt 2; Version conflict → 3 retries → fail with ConcurrentPickException; No conflict → succeed on first attempt; Retry re-reads fresh data (excludes batches depleted by another tx) |

### 13.4 Integration Tests (Testcontainers + SpringBootTest)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `JpaBatchRepositoryTest` | CRUD operations; Unique constraint on (tenant_id, product_id, lot_number, expiration_date); Optimistic locking — version conflict; Tenant isolation — tenant A cannot read tenant B batches; FEFO query returns correct sort order; Partial index query performance |
| `JpaMovementRepositoryTest` | CRUD — create succeeds; UPDATE/DELETE rejected (app user privileges); Tenant isolation; Indexed query by product+date; Batch movement traceability |
| `JpaStockProjectionRepositoryTest` | Upsert behavior; Query by product; Query by status; Rebuild from scratch |
| `JpaLocationRepositoryTest` | CRUD; Unique constraint on (tenant_id, name); Cannot deactivate location with active batches |
| `InventoryControllerTest` | Full REST endpoints with real DB — every POST/GET endpoint tested; Validation error responses; 404 for non-existent entities; 409 for constraint violations; Tenant isolation on all endpoints |

### 13.5 Architecture Tests (ArchUnit)

| Test Class | Rule |
|-----------|------|
| `HexagonalArchitectureTest` | Domain layer has ZERO dependencies on Spring/JPA; Application layer depends ONLY on domain; FefoAllocationStrategy lives in domain, not application; BatchAllocator lives in application layer, not domain; Controllers depend on application services, not domain directly; Repository interfaces are ports in domain; Repository implementations are adapters in infrastructure; No cross-context infrastructure dependencies |

### 13.6 Frontend Tests (Vitest + Testing Library)

| Test File | Scenarios Covered |
|-----------|------------------|
| `BatchReceivingForm.test.tsx` | Renders all form fields; Validates expiration date must be future; Validates quantity > 0; Shows submitting state; Shows server errors; Calls onSubmit with correct data |
| `BatchList.test.tsx` | Renders data rows; Filters by status; Filters by date range; Pagination controls; Empty state; Loading skeleton; Error state with retry |
| `StockCard.test.tsx` | Renders title + value; Color-coded by status; Loading skeleton; Zero value display |
| `MovementTimeline.test.tsx` | Renders chronological entries; Color codes IN (green) and OUT (red); Empty state; "Show more" button; Loading skeleton |
| `StockStatusBadge.test.tsx` | Renders correct color for each status; Renders correct text label; Size variants (sm, md, lg) |

### 13.7 E2E Tests (Playwright)

**Critical path test:**
```
GIVEN: A product exists (created via Product Catalog API)
WHEN:  User receives stock for that product (POST /inventory/batches/receive)
  AND: User views the stock dashboard — StockCard shows updated values
  AND: User creates a sale (via Sales context) consuming some quantity
  AND: User views movements — MovementTimeline shows receipt + sale movements
THEN:  StockCard shows decreased quantity
  AND: Batch detail shows currentQuantity decreased
  AND: Movement record shows both IN and OUT entries
```

---

## 14. Error Handling

### 14.1 Standard Error Response Format

```json
{
  "status": 400,
  "code": "EXPIRATION_IN_PAST",
  "message": "Expiration date must be in the future",
  "correlationId": "req-abc-123-def-456",
  "timestamp": "2026-06-18T12:00:00Z",
  "details": [
    {
      "field": "expirationDate",
      "message": "must be a future date"
    }
  ]
}
```

### 14.2 Exception Mapping

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| `BatchNotFoundException` | 404 | BATCH_NOT_FOUND |
| `ProductNotFoundException` | 404 | PRODUCT_NOT_FOUND |
| `LocationNotFoundException` | 404 | LOCATION_NOT_FOUND |
| `MovementNotFoundException` | 404 | MOVEMENT_NOT_FOUND |
| `DuplicateBatchException` | 409 | DUPLICATE_BATCH |
| `InsufficientStockException` | 409 | INSUFFICIENT_STOCK |
| `BatchExpiredException` | 409 | BATCH_EXPIRED |
| `BatchQuarantinedException` | 409 | BATCH_QUARANTINED |
| `BatchWrittenOffException` | 409 | BATCH_WRITTEN_OFF |
| `ProductDeactivatedException` | 409 | PRODUCT_DEACTIVATED |
| `LocationHasBatchesException` | 409 | LOCATION_HAS_BATCHES |
| `DuplicateLocationException` | 409 | DUPLICATE_LOCATION |
| `ConcurrentPickException` | 409 | CONCURRENT_PICK |
| `ExpirationInPastException` | 400 | EXPIRATION_IN_PAST |
| `InvalidQuantityException` | 400 | INVALID_QUANTITY |
| `InvalidCostException` | 400 | INVALID_COST |
| `ReasonRequiredException` | 400 | REASON_REQUIRED |
| `InvalidTimestampException` | 400 | INVALID_TIMESTAMP |
| `OptimisticLockException` | 409 | OPTIMISTIC_LOCK |
| `TenantMismatchException` | 403 | TENANT_MISMATCH |
| `MovementImmutableException` | 405 | MOVEMENT_IMMUTABLE |
| `ValidationErrorException` | 400 | VALIDATION_ERROR |

### 14.3 Global Error Handling

A `@RestControllerAdvice` SHALL handle all exceptions globally. Every response MUST include:
- `correlationId` (generated per request, logged for traceability)
- `timestamp` (ISO 8601)
- Structured `details` array for field-level validation errors

---

## Appendix A: Design Decisions Log

| ID | Decision | Rationale | Date |
|----|----------|-----------|------|
| INV-DEC-01 | FEFO engine as dedicated domain component (AllocationStrategy + FefoAllocationStrategy + BatchAllocator) | Isolated from Sales, can be tested independently, can swap strategy without affecting clients | 2026-06-18 |
| INV-DEC-02 | StockProjection as event-updated read model | Avoids dual-write problem. Eventual consistency acceptable for pharmacy use cases | 2026-06-18 |
| INV-DEC-03 | **version in domain Batch** (unlike Product Catalog where version is JPA-only) | FEFO allocation engine needs to detect concurrent modifications in application layer for retry logic. Domain treats version as opaque (read-only); application layer drives retry | 2026-06-18 |
| INV-DEC-04 | Movements are append-only, no update/delete | Regulatory requirement: audit trail must be immutable. Corrections = new ADJUSTMENT movement | 2026-06-18 |
| INV-DEC-05 | Simplified stock for MVP (physical + available only) | Quarantine, reserved, in-transit add complexity not needed for initial launch | 2026-06-18 |
| INV-DEC-06 | Location as persisted entity (FK from Batch) | Referential integrity for batch storage tracking. Pharmacies define their storage layout. FEFO may consider location type in future | 2026-06-18 |
| INV-DEC-07 | Stock projection rebuilt from batches on startup | Safety net for projection drift. Ensures eventual consistency | 2026-06-18 |
| INV-DEC-08 | QUARANTINED → ACTIVE transition is post-MVP | Quarantine reactivation requires quality validation workflow. MVP: QUARANTINED can only go to WRITTEN_OFF | 2026-06-18 |
| INV-DEC-09 | `SELECT ... FOR UPDATE` on FEFO read + optimistic version on write | Two-phase lock strategy prevents phantom reads (FOR UPDATE) AND detects stale writes (version). Tradeoff: lower concurrency, acceptable for pharmacy volumes | 2026-06-18 |
| INV-DEC-10 | Batch status is RE-computed, not stale | NEAR_EXPIRY and EXPIRED statuses are recalculated by scheduled jobs, not stored as permanent state. The `status` column is updated on transition | 2026-06-18 |
| INV-DEC-11 | Movement source field distinguishes origin | `source` field (UI | API | BATCH | SYSTEM) enables audit differentiation between user-initiated and system-generated movements | 2026-06-18 |
| INV-DEC-12 | FEFO includes NEAR_EXPIRY in eligible statuses | NEAR_EXPIRY batches are still safe for consumption. Excluding them would defeat FEFO's purpose. Only EXPIRED and QUARANTINED are excluded | 2026-06-18 |

## Appendix B: Database Schema (Flyway V2)

```sql
-- V2__create_schema_inventory.sql

CREATE SCHEMA IF NOT EXISTS inventory;

CREATE TABLE inventory.locations (
    location_id  UUID PRIMARY KEY,
    tenant_id    VARCHAR(36) NOT NULL,
    name         VARCHAR(100) NOT NULL,
    type         VARCHAR(20) NOT NULL,  -- SHELF, FRIDGE, CONTROLLED, QUARANTINE
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_locations_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE inventory.batches (
    batch_id           UUID PRIMARY KEY,
    tenant_id          VARCHAR(36) NOT NULL,
    product_id         UUID NOT NULL,
    lot_number         VARCHAR(100) NOT NULL,
    manufacturing_date DATE,
    expiration_date    DATE NOT NULL,
    initial_quantity   INTEGER NOT NULL CHECK (initial_quantity > 0),
    current_quantity   INTEGER NOT NULL CHECK (current_quantity >= 0),
    location_id        UUID REFERENCES inventory.locations(location_id),
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    cost_per_unit      DECIMAL(12,2) NOT NULL CHECK (cost_per_unit > 0),
    received_date      TIMESTAMP WITH TIME ZONE NOT NULL,
    notes              TEXT,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_batches_product_lot_expiry UNIQUE (tenant_id, product_id, lot_number, expiration_date),
    CONSTRAINT chk_batch_quantity CHECK (current_quantity <= initial_quantity)
);

CREATE INDEX idx_batches_product_status
    ON inventory.batches (tenant_id, product_id, status);
CREATE INDEX idx_batches_fefo_pick
    ON inventory.batches (tenant_id, product_id, status, expiration_date, received_date)
    WHERE status IN ('ACTIVE', 'NEAR_EXPIRY') AND current_quantity > 0;
CREATE INDEX idx_batches_expiry_date
    ON inventory.batches (expiration_date)
    WHERE status IN ('ACTIVE', 'NEAR_EXPIRY');

CREATE TABLE inventory.movements (
    movement_id    UUID PRIMARY KEY,
    tenant_id      VARCHAR(36) NOT NULL,
    movement_type  VARCHAR(20) NOT NULL,
    direction      VARCHAR(3) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    quantity       INTEGER NOT NULL CHECK (quantity > 0),
    product_id     UUID NOT NULL,
    batch_id       UUID REFERENCES inventory.batches(batch_id),
    reference_type VARCHAR(50),
    reference_id   UUID,
    unit_price     DECIMAL(12,2),
    total_value    DECIMAL(12,2),
    reason         TEXT,
    performed_by   VARCHAR(100) NOT NULL,
    performed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    notes          TEXT,
    source         VARCHAR(20) NOT NULL DEFAULT 'API'
);

CREATE INDEX idx_movements_product
    ON inventory.movements (tenant_id, product_id, performed_at DESC);
CREATE INDEX idx_movements_batch
    ON inventory.movements (tenant_id, batch_id, performed_at DESC);
CREATE INDEX idx_movements_type_date
    ON inventory.movements (tenant_id, movement_type, performed_at DESC);

CREATE TABLE inventory.stock_projection (
    product_id          UUID PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    total_quantity      INTEGER NOT NULL DEFAULT 0,
    available_quantity  INTEGER NOT NULL DEFAULT 0,
    batch_count         INTEGER NOT NULL DEFAULT 0,
    next_expiration_date DATE,
    expired_quantity    INTEGER NOT NULL DEFAULT 0,
    value_at_cost       DECIMAL(14,2) NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'OUT_OF_STOCK',
    last_updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_stock_projection_status
    ON inventory.stock_projection (tenant_id, status);
```
