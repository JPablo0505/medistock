# MediStock — Domain Exploration & Architecture Blueprint

> **Phase:** SDD Explore (Standalone)
> **Date:** 2026-06-18
> **Status:** Complete — ready for SDD Proposal phase

---

## Table of Contents

1. [Domain Overview](#1-domain-overview)
2. [Ubiquitous Language](#2-ubiquitous-language)
3. [Aggregate Candidates](#3-aggregate-candidates)
4. [Bounded Contexts](#4-bounded-contexts)
5. [Domain Events](#5-domain-events)
6. [FEFO Algorithm Considerations](#6-fefo-algorithm-considerations)
7. [Key Business Rules](#7-key-business-rules)
8. [Architecture Considerations](#8-architecture-considerations)
9. [Risks and Complexities](#9-risks-and-complexities)
10. [UI Design Mapping](#10-ui-design-mapping)

---

## 1. Domain Overview

### What MediStock Does

MediStock is a **pharmaceutical inventory management SaaS** designed for independent pharmacies and pharmacy chains. It solves the unique regulatory and operational challenges of managing pharmaceutical stock — where expiration dates are not just a quality concern but a **safety and compliance requirement**.

### Core Business Processes

```
Pharmacy Operations Lifecycle:
┌──────────────────────────────────────────────────────────┐
│  1. PROCUREMENT                                          │
│     Purchase Order → Receiving → Batch Registration      │
│                                                          │
│  2. WAREHOUSING                                          │
│     FEFO Slotting → Stock Keeping → Transfer Between     │
│     Locations (shelf, fridge, controlled substance safe) │
│                                                          │
│  3. SALES                                                │
│     POS Integration / Manual Entry → FEFO Picking →      │
│     Sale Registration → Stock Deduction                  │
│                                                          │
│  4. EXPIRATION MANAGEMENT                                │
│     Near-Expiry Alerts → Quarantine → Write-Off →        │
│     Disposal Documentation                               │
│                                                          │
│  5. INVENTORY CONTROL                                    │
│     Cycle Counts → Adjustments → Audit Trail →           │
│     Regulatory Reports                                   │
│                                                          │
│  6. RETURNS                                              │
│     Customer Return → Quality Check → Restock / Write-Off│
│                                                          │
│  7. REPORTING                                            │
│     Valuation → Turnover → Expiration → Movement Audit   │
└──────────────────────────────────────────────────────────┘
```

### Who Are the Users?

| Role | Primary Goals | Pain Points Today |
|------|--------------|-------------------|
| **Pharmacist / Pharmacy Manager** | Ensure stock accuracy, prevent expired sales, manage alerts | Spreadsheets, paper logs, fragmented systems |
| **Pharmacy Technician** | Quick product lookup, FEFO picking guidance, receiving shipments | Manual expiry checking, no picking guidance |
| **Chain Operations Manager** | Multi-location visibility, inventory valuation, turnover analysis | No centralized view, inconsistent practices |
| **Regulatory Compliance Officer** | Audit trail, expired product control, disposal documentation | Manual record-keeping, high risk of non-compliance |
| **Procurement / Purchasing Agent** | Reorder decisions based on stock levels and expiry data | Scattered data, no batch-level visibility |

### Key Differentiators vs. Generic Inventory Systems

- FEFO (First Expiry, First Out) is NOT optional — it's enforced by the system
- Batch/lot traceability is mandatory, not a nice-to-have
- Expired products are automatically quarantined — cannot be sold
- Every movement has a complete audit trail (who, what, when, why, reference)
- Multi-location support (shelf, fridge, controlled substance, quarantine)

---

## 2. Ubiquitous Language

### Core Terms (Bilingual)

| English | Spanish | Definition |
|---------|---------|------------|
| **Product** | Producto | A catalog item — the generic pharmaceutical entity (e.g., "Ibuprofeno 400mg"). Has a SKU, name, laboratory, presentation. |
| **SKU** | SKU (código) | Stock Keeping Unit — unique identifier for a product. May be internal or manufacturer-provided (GTIN, EAN). |
| **Batch / Lot** | Lote | A specific production/ manufacturing run of a product. Identified by a lot number assigned by the manufacturer. Contains the concrete items with the same expiration date. |
| **Expiration Date** | Fecha de vencimiento | The date after which a batch is considered unsafe for consumption. Cannot be sold after this date. |
| **Manufacturing Date** | Fecha de fabricación | Date the batch was manufactured. Used for quality investigations but not for picking logic. |
| **Stock** | Stock / Existencia | Current quantity of a product in a specific location. Can be positive or zero (never negative). |
| **Available Stock** | Stock disponible | Total stock of a product minus any reserved/committed quantities. |
| **Movement** | Movimiento | A recording of a stock change. Immutable once recorded. Contains direction (+/-), quantity, reason, reference, user, timestamp. |
| **Movement Type** | Tipo de movimiento | Classification: RECEIPT, SALE, RETURN, TRANSFER_IN, TRANSFER_OUT, ADJUSTMENT, WRITE_OFF. |
| **FEFO** | FEFO (Primer Vencimiento, Primer en Salir) | First Expiry, First Out — the picking algorithm that selects batches closest to expiration first. |
| **Location** | Ubicación | A physical storage spot (e.g., "Shelf A-3", "Refrigerator 2", "Controlled Substance Safe"). |
| **Warehouse** | Depósito | A physical facility. A pharmacy may have one warehouse (the pharmacy itself) or multiple. |
| **Alert** | Alerta | A notification rule (e.g., "alert when stock < reorder point") or a specific alert instance. |
| **Alert Threshold** | Umbral de alerta | Configurable values: low stock level, days before expiry for warning, max movement velocity. |
| **Quarantine** | Cuarentena | A virtual/physical location for products that cannot be sold (expired, damaged, recalled). |
| **Write-Off** | Baja / Despacho | Permanent removal from inventory (expired, damaged, stolen). |
| **Cycle Count** | Conteo cíclico | A physical inventory count of a subset of products, used to reconcile system vs. actual stock. |
| **Adjustment** | Ajuste | A movement that corrects inventory based on cycle count or discrepancy found. |
| **Receipt / Goods Receipt** | Recepción | The process of registering incoming inventory from a supplier. Each receipt creates batches. |
| **Purchase Order** | Orden de compra | A document requesting products from a supplier. Not yet received. |
| **Serial Number** | Número de serie | Individual item identifier (for controlled substances or high-value items). Optional, product-dependent. |
| **Reserved Stock** | Stock reservado | Quantity committed to a pending sale or order but not yet picked. |
| **Turnover** | Rotación | How fast inventory sells. Calculated as cost of goods sold / average inventory value. |
| **Reconciliation** | Conciliación | The process of matching system stock with physical stock. |
| **GTIN / EAN** | GTIN / EAN | Global Trade Item Number — barcode standard for product identification. |
| **Dispensing** | Dispensa | The act of preparing and giving medication to a patient. In retail pharmacy, this is the "sale" step. |

### Concepts That MUST NOT Be Confused

```
Product ≠ Batch
  Product: "Ibuprofeno 400mg, 30 tablets" (one catalog entry)
  Batch:   "Ibuprofeno 400mg, Lot# L2405A, exp 2026-12-15" (specific instance)

Stock (product level) ≠ Stock (batch level)
  Product stock: sum of all batch quantities for that product
  Batch stock:   quantity remaining in that specific batch

Movement ≠ Adjustment
  Movement: any stock change (broad term)
  Adjustment: a movement type specifically for corrections (narrow term)
```

---

## 3. Aggregate Candidates

Following DDD principles, these are the proposed aggregates. Each has a clear **aggregate root**, **invariants** it protects, and **boundaries** that define transaction consistency.

### 3.1 Product Aggregate

```
Product (AGGREGATE ROOT)
├── productId: UUID
├── sku: string (unique, business key)
├── name: string
├── description: string
├── laboratory: string (manufacturer)
├── presentation: string (e.g., "30 tablets", "100ml bottle")
├── gtin: string (optional, barcode)
├── requiresSerialTracking: boolean
├── requiresColdChain: boolean
├── isControlledSubstance: boolean
├── categoryId: reference to Category
├── defaultReorderPoint: quantity
├── defaultMaxStock: quantity
├── averageUnitCost: money
├── isActive: boolean
├── createdAt: timestamp
└── updatedAt: timestamp
```

**Invariants:**
- SKU must be unique across the tenant
- GTIN/EAN must be valid format if provided
- Cannot delete a product that has stock or active batches

**Commands:**
- `registerProduct(data: NewProductData): Product`
- `updateProduct(productId, data: UpdateProductData): Product`
- `deactivateProduct(productId): Product`

---

### 3.2 Batch / Lot Aggregate

```
Batch (AGGREGATE ROOT)
├── batchId: UUID
├── productId: reference to Product
├── lotNumber: string (manufacturer-assigned)
├── manufacturingDate: date
├── expirationDate: date (MANDATORY)
├── initialQuantity: int
├── currentQuantity: int (≥ 0)
├── locationId: reference to Location
├── status: BatchStatus (ACTIVE | NEAR_EXPIRY | EXPIRED | QUARANTINED | DEPLETED | WRITTEN_OFF)
├── costPerUnit: money
├── receivedDate: timestamp
├── receivedVia: movementId (reference to the receipt movement)
├── notes: string
├── serialNumbers: SerialNumber[] (optional, only if product requires)
└── version: long (optimistic lock for concurrent FEFO picks)

BatchStatus enum:
  ACTIVE      → Available for sale
  NEAR_EXPIRY → Within warning window (configurable, e.g., 90 days)
  EXPIRED     → Past expiration date, auto-quarantined
  QUARANTINED → Manually or automatically blocked
  DEPLETED    → currentQuantity = 0
  WRITTEN_OFF → Officially written off with documentation
```

**Invariants:**
- `expirationDate > receivedDate` (cannot receive already-expired stock)
- `currentQuantity ≥ 0` (never negative)
- `currentQuantity ≤ initialQuantity` (cannot exceed received amount)
- `lotNumber + productId + expirationDate` is unique per tenant (a batch is identified by its product + lot + expiry)
- Expired batches cannot be sold (status automatically moves to EXPIRED)
- NEAR_EXPIRY status calculated from configurable threshold

**Commands:**
- `receiveBatch(productId, lotNumber, expirationDate, quantity, ...) : Batch`
- `quarantineBatch(batchId, reason) : Batch`
- `writeOffBatch(batchId, reason, documentation) : Batch`
- `transferBatch(batchId, targetLocationId, quantity) : Batch`
- `splitBatch(batchId, quantity, newLocationId) : Batch` (for partial location transfers)

---

### 3.3 Stock Projection (Value Object / Read Model)

**Important:** Stock is NOT an aggregate — it's a **derived projection** computed from Batch data.

```
StockProjection (READ MODEL — not persisted, computed on demand)
├── productId: UUID
├── totalQuantity: int (sum of all ACTIVE batch quantities)
├── availableQuantity: int (total - reserved)
├── batchCount: int
├── nextExpirationDate: date (soonest expiring batch)
├── valueAtCost: money (sum of batch quantities × costPerUnit)
└── status: StockStatus (IN_STOCK | LOW_STOCK | OUT_OF_STOCK | OVERSTOCKED)
```

**Rationale:** Making Stock an aggregate would create a dual-write problem — every movement would need to update both the movement and the stock counter. Instead, stock is always computed from the aggregate root (Batches). This is a **true DDD decision**: the "current stock position" is a snapshot, not a transactional entity.

For performance, a **materialized view** or **projection table** can be updated asynchronously (event-driven). But the **source of truth** is always the Batch records.

---

### 3.4 Movement Aggregate

```
Movement (AGGREGATE ROOT) — IMMUTABLE
├── movementId: UUID
├── movementType: MovementType (RECEIPT | SALE | RETURN | TRANSFER_IN | TRANSFER_OUT | ADJUSTMENT | WRITE_OFF)
├── direction: MovementDirection (IN | OUT)
├── quantity: int (> 0, always positive — direction indicates sign)
├── productId: UUID
├── batchId: UUID (nullable — for non-batch-specific movements like global adjustments)
├── referenceType: string (e.g., "PURCHASE_ORDER", "SALE", "CYCLE_COUNT")
├── referenceId: UUID (foreign key to the originating document)
├── unitPrice: money (at time of movement, for valuation)
├── totalValue: money (unitPrice × quantity)
├── reason: string (free text for adjustments/write-offs)
├── performedBy: userId
├── performedAt: timestamp (MANDATORY — when the physical movement happened)
├── recordedAt: timestamp (when entered into system — may differ from performedAt)
├── notes: string
└── source: string (API | BATCH | UI)

MovementType enum:
  RECEIPT       → IN:  Purchase order receipt
  SALE          → OUT: Sale to customer
  RETURN        → IN:  Customer return
  TRANSFER_OUT  → OUT: Moved to another location
  TRANSFER_IN   → IN:  Received from another location
  ADJUSTMENT    → ±:   Cycle count correction
  WRITE_OFF     → OUT: Expired/damaged disposal
```

**Invariants:**
- Movements are **immutable** once created (append-only log)
- A movement cannot be deleted or edited — corrections require a new ADJUSTMENT movement
- `quantity` is always positive; `direction` indicates the sign
- Every OUT movement must specify which batch it came from (enforces traceability)
- A SALE movement must reference an existing Sale aggregate
- A RECEIPT movement creates the associated Batch

**Commands:**
- `recordMovement(data: NewMovementData): Movement`
- Movements are created by domain services, not directly (e.g., `FefoPickService` creates SALE movements)

---

### 3.5 Sale Aggregate

```
Sale (AGGREGATE ROOT)
├── saleId: UUID
├── saleDate: timestamp
├── saleType: SaleType (POS_INTEGRATION | MANUAL_ENTRY)
├── customerName: string (optional — some pharmacies track)
├── prescriptionNumber: string (optional, regulated requirement)
├── items: SaleItem[] (≥ 1)
│   ├── productId: UUID
│   ├── productName: string (snapshot at time of sale)
│   ├── quantity: int
│   ├── unitPrice: money
│   ├── totalPrice: money
│   └── batchAllocations: BatchAllocation[] (FEFO-picked batches)
│       ├── batchId: UUID
│       ├── quantity: int (from this batch)
│       └── costPerUnit: money
├── subtotal: money
├── tax: money
├── total: money
├── performedBy: userId
├── status: SaleStatus (COMPLETED | VOIDED)
└── voidedByMovementId: UUID (nullable, links to void movement if reversed)
```

**Invariants:**
- A sale MUST use FEFO batch allocation (enforced by domain service)
- Cannot sell expired batches
- Total quantity per product must equal sum of batch allocations
- Voided sales must generate reverse movements (stock goes back)
- Price snapshot at time of sale (product price may change later)

**Commands:**
- `createSale(items: SaleItemData[], ...): Sale` (triggers FEFO allocation + movements)
- `voidSale(saleId, reason): Sale` (triggers reverse movements)

---

### 3.6 Alert Aggregate

```
AlertRule (AGGREGATE ROOT)
├── alertRuleId: UUID
├── name: string
├── alertType: AlertType (LOW_STOCK | NEAR_EXPIRY | EXPIRED | UNUSUAL_MOVEMENT | BATCH_QUARANTINED)
├── conditions: AlertCondition (JSON)
│   └─ depends on type:
│       LOW_STOCK:       { productId?, threshold: quantity }
│       NEAR_EXPIRY:     { daysBeforeExpiry: int }
│       EXPIRED:         { checkFrequency: cron }
│       UNUSUAL_MOVEMENT: { maxDailyQuantity, productId? }
│       BATCH_QUARANTINED: {}  (always trigger)
├── isEnabled: boolean
├── notificationChannels: NotificationChannel[] (IN_APP | EMAIL)
├── createdAt: timestamp
└── updatedAt: timestamp

AlertInstance (ENTITY — child of AlertRule)
├── alertInstanceId: UUID
├── alertRuleId: reference to AlertRule
├── productId: UUID (nullable)
├── batchId: UUID (nullable)
├── severity: AlertSeverity (INFO | WARNING | CRITICAL)
├── message: string
├── isRead: boolean
├── isResolved: boolean
├── triggeredAt: timestamp
└── resolvedAt: timestamp (nullable)
```

**Invariants:**
- An `EXPIRED` alert must fire the instant a batch's expiration date passes
- `NEAR_EXPIRY` alerts should not re-fire daily for the same batch (idempotency)
- `LOW_STOCK` alerts fire on state change only (e.g., entering LOW_STOCK, not every check)
- Unread CRITICAL alerts must be prominently visible on dashboard

**Commands:**
- `createAlertRule(data): AlertRule`
- `evaluateAlertRules(): AlertInstance[]` (scheduled or event-driven)
- `dismissAlert(instanceId): void`
- `resolveAlert(instanceId): void`

---

### 3.7 Aggregate Relationships Diagram

```
┌──────────┐     references     ┌──────────┐
│ Product  │◄────────────────────│  Batch   │
└──────────┘   1 : N            └────┬─────┘
       │                              │
       │ derived from                  │ allocated in
       ▼                              ▼
┌──────────────────┐        ┌──────────────────┐
│ StockProjection  │        │    Movement      │
│ (READ MODEL)     │        │ (IMMUTABLE LOG)  │
└──────────────────┘        └────────┬─────────┘
                                     │
                                     │ referenced by
                                     ▼
                            ┌──────────────────┐
                            │      Sale        │
                            │   (with FEFO     │
                            │   allocations)   │
                            └──────────────────┘

┌──────────────────┐
│   AlertRule      │─── has instances ────┐
└──────────────────┘                      ▼
                                  ┌──────────────────┐
                                  │  AlertInstance   │
                                  └──────────────────┘
```

### Transaction Boundaries

| Use Case | Aggregates Modified | Notes |
|----------|-------------------|-------|
| Receiving stock | Batch (+ Movement) | One transaction: create batch + record receipt movement |
| Selling (FEFO) | Sale (+ Movements + Batch quantities) | **Critical:** Update batch quantities AND create movements AND create sale in ONE transaction. This is where optimistic locking on Batch is essential. |
| Stock adjustment | Movement (+ possibly Batch) | Adjustment movement only, or also adjust a specific Batch |
| Expiry check (batch → expired) | Batch (status change) + Movement (write-off) | Populates AlertInstances, does NOT touch Batches |
| Alert evaluation | AlertInstance | Read-only on StockProjection, writes only Alert |

---

## 4. Bounded Contexts

The domain naturally divides into these bounded contexts, each with its own ubiquitous language, business rules, and database schema (schema-per-context).

### 4.1 Product Catalog Context

**Purpose:** Manage the product master data — what products exist, their attributes, categorization.

**Aggregates:** Product, Category, Laboratory

**Key Responsibilities:**
- Product CRUD
- SKU/GTIN uniqueness enforcement
- Product categorization
- Product attribute management
- Import/export product catalog

**Dependencies:** None (foundational context)

**Events Emitted:**
- `ProductRegistered`
- `ProductUpdated`
- `ProductDeactivated`

### 4.2 Inventory Management Context

**Purpose:** Track stock positions, manage locations, handle all stock movements.

**Aggregates:** Batch, Movement, Location

**Key Responsibilities:**
- Batch/lot registration and tracking
- FEFO picking logic (core algorithm)
- Movement recording (append-only)
- Stock location management
- Inventory reconciliation (cycle counts)
- Write-off and quarantine management

**Dependencies:** Product Catalog (product reference)

**Events Emitted:**
- `BatchReceived`
- `BatchExpired` (scheduled check)
- `BatchNearExpiry` (scheduled check)
- `BatchDepleted`
- `BatchQuarantined`
- `MovementRecorded`
- `StockDepleted`
- `StockReplenished`

### 4.3 Alerting Context

**Purpose:** Define alert rules, evaluate conditions, deliver notifications.

**Aggregates:** AlertRule, AlertInstance

**Key Responsibilities:**
- Alert rule CRUD
- Alert evaluation engine (scheduled + event-driven)
- Alert deduplication and escalation
- Notification delivery (in-app, email)
- Alert read/resolve lifecycle

**Dependencies:** Product Catalog, Inventory Management (reads stock/batch data)

**Events Emitted:**
- `LowStockAlertTriggered`
- `NearExpiryAlertTriggered`
- `ExpiredAlertTriggered`
- `BatchQuarantinedAlertTriggered`

**Events Consumed:**
- `BatchExpired` (triggers expired alert)
- `MovementRecorded` (triggers low stock recheck)
- `BatchQuarantined` (triggers quarantine alert)

### 4.4 Sales Context

**Purpose:** Record sales transactions with FEFO-compliant batch allocation.

**Aggregates:** Sale

**Key Responsibilities:**
- Sale creation (POS integration or manual entry)
- FEFO batch allocation at sale time
- Sale voiding with stock reversal
- Price snapshot at time of sale
- Prescription number tracking (optional, regulated)

**Dependencies:** Inventory Management (batch data, FEFO), Product Catalog (product data, prices)

**Events Emitted:**
- `SaleCompleted`
- `SaleVoided`

**Events Consumed:**
- None (Sales writes movements, doesn't consume them)

### 4.5 Reporting Context

**Purpose:** Generate all reports — valuation, turnover, expiration, movement audit.

**Aggregates:** None (pure read side). This is a query context.

**Key Responsibilities:**
- Inventory valuation report (FIFO or weighted average cost)
- Turnover analysis report
- Expiration report (batches expiring in date range)
- Movement audit trail
- Low stock / overstock report
- Multi-location comparison

**Dependencies:** All other contexts (reads projections)

**Key Queries:**
- `getStockValuationAsOf(date): ValuationReport`
- `getExpirationReport(dateRange): ExpirationReport`
- `getMovementAudit(dateRange, filters): MovementAuditReport`
- `getInventoryTurnover(period): TurnoverReport`

### 4.6 Bounded Context Map

```
┌─────────────────────────────────────────────────────────────┐
│                     BOUNDED CONTEXTS MAP                     │
│                                                             │
│  ┌─────────────────┐   ┌──────────────────┐                 │
│  │ Product Catalog │   │  Sales Context   │                 │
│  │ (foundational)  │──▶│ (FEFO allocation)│                 │
│  └────────┬────────┘   └────────┬─────────┘                 │
│           │                     │                          │
│           │                     │ writes movements          │
│           ▼                     ▼                          │
│  ┌─────────────────────────────────────────┐               │
│  │       Inventory Management Context       │               │
│  │  (Batch, Movement, FEFO engine, Stock)   │               │
│  └────────┬──────────────┬──────────────────┘               │
│           │              │                                  │
│           ▼              ▼                                  │
│  ┌─────────────────┐   ┌──────────────────┐                 │
│  │  Alerting       │   │   Reporting      │                 │
│  │  Context        │   │   Context (RO)   │                 │
│  └─────────────────┘   └──────────────────┘                 │
│                                                             │
│  Arrows = DEPENDENCY direction                              │
│  All read the Inventory Management projection               │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. Domain Events

### 5.1 Event Catalog

| Event | Source Context | Payload | Consumers |
|-------|---------------|---------|-----------|
| `ProductRegistered` | Product Catalog | `{ productId, sku, name, categoryId }` | Inventory (prepares for batches), Reporting |
| `ProductUpdated` | Product Catalog | `{ productId, changedFields }` | Reporting |
| `ProductDeactivated` | Product Catalog | `{ productId }` | Inventory (prevents new receipts) |
| `BatchReceived` | Inventory | `{ batchId, productId, lotNumber, expirationDate, quantity, locationId }` | Alerting (evaluate low stock), Reporting |
| `BatchExpired` | Inventory (scheduled) | `{ batchId, productId, lotNumber, quantity, expirationDate }` | Alerting (create critical alert), Sales (block picking) |
| `BatchNearExpiry` | Inventory (scheduled) | `{ batchId, productId, lotNumber, quantity, daysUntilExpiry }` | Alerting (create warning alert) |
| `BatchDepleted` | Inventory | `{ batchId, productId, lotNumber }` | Alerting (depleted batch, may trigger low stock re-eval) |
| `BatchQuarantined` | Inventory | `{ batchId, productId, lotNumber, reason, quantity }` | Alerting (create critical alert), Sales (block picking) |
| `MovementRecorded` | Inventory | `{ movementId, type, direction, productId, batchId?, quantity, performedAt }` | Alerting (re-evaluate low stock), Reporting |
| `StockDepleted` | Inventory (derived) | `{ productId, sku, name }` | Alerting (trigger LOW_STOCK alert) |
| `StockReplenished` | Inventory (derived) | `{ productId, newQuantity }` | Alerting (resolve LOW_STOCK alert) |
| `SaleCompleted` | Sales | `{ saleId, items: [{ productId, quantity, batchAllocations }], total, performedAt }` | Inventory (movements already created), Reporting |
| `SaleVoided` | Sales | `{ saleId, reason }` | Inventory (reverse movements created inline) |
| `LowStockAlertTriggered` | Alerting | `{ alertInstanceId, productId, currentStock, threshold }` | Notification delivery |
| `NearExpiryAlertTriggered` | Alerting | `{ alertInstanceId, batchId, productId, daysUntilExpiry }` | Notification delivery |
| `ExpiredAlertTriggered` | Alerting | `{ alertInstanceId, batchId, productId, quantity }` | Notification delivery |

### 5.2 Event Flow for Critical Path: Sale with FEFO

```
1. Sales.createSale(items) command received
2. FefoPickService.execute(productId, requestedQuantity)
   │
   ├─ Reads all ACTIVE batches for product, ordered by expirationDate ASC
   │  (optimistic lock on each batch read)
   │
   ├─ Allocates: take from soonest-expiring batch first,
   │  then next, until requested quantity is fulfilled
   │
   ├─ For each batch allocation:
   │   ├─ batch.currentQuantity -= allocatedQuantity (optimistic lock check)
   │   ├─ Movement.create (SALE, OUT, productId, batchId, quantity, salePrice)
   │   │
   │   └─ if batch.currentQuantity == 0:
   │       Event: BatchDepleted { batchId }
   │
   ├─ Sale item records batchAllocations
   │
   └─ if product's total remaining stock == 0:
       Event: StockDepleted { productId }
   │
3. Sale aggregate created with all items and allocations
4. Event: SaleCompleted { saleId, ... }
5. Alerting consumes StockDepleted → creates alert
6. Alerting consumes BatchDepleted → re-evaluates product alert state
```

### 5.3 Eventing Strategy

**Phase 1 (MVP):** Application events within the same JVM (Spring `ApplicationEventPublisher` + `@TransactionalEventListener`). This keeps things simple and transactional.

**Phase 2 (Scale):** Move to an outbox pattern + message broker (RabbitMQ / Redis Streams) for cross-context eventual consistency. The outbox ensures at-least-once delivery without 2PC.

---

## 6. FEFO Algorithm Considerations

### 6.1 Core Algorithm

```
FEFO Pick = SELECT batches
  WHERE productId = :productId
    AND status = ACTIVE
    AND currentQuantity > 0
  ORDER BY expirationDate ASC, receivedDate ASC
```

**Why `expirationDate ASC`?** — The batch that expires soonest must be picked first.

**Why `receivedDate ASC` as secondary sort?** — Tiebreaker when two batches have the same expiration date. The older batch (received first) goes out first. This aligns with FIFO within the same expiry.

### 6.2 Edge Cases

| Edge Case | Resolution |
|-----------|-----------|
| **Same expiration date, multiple batches** | Order by `receivedDate ASC` (FIFO tiebreaker). Also consider: if same expiry, older stock has higher risk of quality degradation. |
| **Requested quantity > all available batches combined** | **Cannot fulfill.** Sale must be rejected or partially fulfilled. Domain rule: never oversell. |
| **No ACTIVE batches for product** | Stock is zero. Reject sale, trigger LOW_STOCK alert if configured. |
| **All batches ACTIVE but some near-expiry** | FEFO naturally picks near-expiry first. That's correct — it's the intended behavior. |
| **Customer return of an expired product** | Cannot return to inventory. The returned item must go to QUARANTINE with a RETURN movement, then immediate WRITE_OFF. |
| **Batch partially quarantined** (e.g., 50 of 100 units damaged) | Split the batch: create a QUARANTINED batch with 50 units, keep ACTIVE with 50 units. FEFO only sees the ACTIVE portion. |
| **Concurrent FEFO picks for the same product** (two cashiers selling simultaneously) | **Optimistic locking on Batch.** Each batch has a `version` column. When updating `currentQuantity`, check `WHERE version = :expected`. If another pick already modified the batch, the transaction fails and must retry with fresh data. |
| **Same product, same expiry, one batch has lower unit cost** | Irrelevant for FEFO. Cost is not a picking criterion. However, for valuation purposes, we track cost at the movement level. |
| **FEFO with reserved stock** | Reserved stock must be excluded from available quantity for FEFO picks. If 10 units are reserved for an order, they're not available for the current sale. |
| **Negative adjustment on a batch** | If an adjustment reduces a batch below zero, it's a system error. Adjustments should target the product, not a specific batch, or be validated to never exceed batch quantity. |

### 6.3 FEFO and Returns

**Returned products are a special challenge:**

1. Items returned by a customer have a **new "received" date** but the **original expiration date**.
2. Should returned items be FEFO-picked before fresh stock with the same expiry?
3. **Policy decision:** Returned items go to the front of the FEFO queue (same expiration date, earliest received date wins — the return happened after the original receipt, so `receivedDate` sort handles this naturally).
4. However, some pharmacies quarantine returns for quality inspection before restocking. This is a configurable workflow.

### 6.4 Performance Considerations

- FEFO picking requires `ORDER BY expirationDate, receivedDate` — an index on `(productId, status, expirationDate, receivedDate)` is essential.
- Optimistic locking for concurrent picks is fine for pharmacy volumes (a busy pharmacy does ~200-500 sales/day, not thousands per minute).
- For chains, consider batch-level locking vs. product-level locking:
  - **Batch-level:** Locks only the batches being picked. Other products unaffected. Higher concurrency.
  - **Product-level:** Locks all batches of a product during pick. Safer but lower concurrency.
  - **Recommendation:** Batch-level with retry.

---

## 7. Key Business Rules

### 7.1 Regulatory Rules (Non-Negotiable)

| Rule ID | Rule | Enforcement |
|---------|------|-------------|
| R001 | Expired products MUST NOT be sold | System MUST reject any sale containing an expired batch. Status check before FEFO allocation. |
| R002 | Batch/lot traceability MUST be maintained for all prescription products | Every OUT movement MUST reference a batch ID. No orphan stock deductions. |
| R003 | Inventory records MUST be accurate within regulatory tolerance (±2% for controlled substances) | Cycle counts + adjustments + audit trail. Controlled substances need serial-level tracking. |
| R004 | Expired product disposal MUST be documented | Write-off movements require reason + documentation reference. |
| R005 | Controlled substances MUST have serial-level tracking | Product-level flag `isControlledSubstance`. When true, each unit within a batch has a serial number. |
| R006 | Temperature-sensitive products MUST have location tracking (cold chain) | Product-level flag `requiresColdChain`. Batch location must be a cold-storage location. |

### 7.2 Operational Rules

| Rule ID | Rule | Enforcement |
|---------|------|-------------|
| O001 | Stock quantity MUST NEVER go negative | All OUT movements validated against available quantity before creation. |
| O002 | FEFO picking is the DEFAULT and ONLY picking strategy | No "pick any batch" option in the standard sale flow. Exceptions (manual override) require manager authorization and audit log. |
| O003 | Every stock change MUST be recorded as a Movement | No direct SQL updates to batch quantities bypassing the domain. |
| O004 | Movements are IMMUTABLE once recorded | No DELETE or UPDATE on movements table. Corrections = new ADJUSTMENT movement. |
| O005 | Low stock alerts fire on state TRANSITION, not continuously | An alert fires when entering LOW_STOCK state. It does not re-fire daily. Resolved when stock exceeds threshold + buffer. |
| O006 | Near-expiry alerts are DAILY (reminder) | Configured X days before expiry. Fires once per batch per day until resolved (batch is sold, expires, or is written off). |
| O007 | A batch cannot have negative quantity | Validated on every OUT movement from that batch. |
| O008 | A sale can be voided, not deleted | Void creates reverse movements that restore stock. The original sale and movements remain in the audit log. |
| O009 | Price is captured at time of sale, not looked up from Product | Product price may change; the sale record has its own price snapshot. |

### 7.3 Configuration Rules (Tenant-Specific)

| Rule ID | Rule | Default |
|---------|------|---------|
| C001 | Near-expiry warning window (days) | 90 days |
| C002 | Low stock threshold per product | Specified per-product in Product aggregate |
| C003 | Maximum stock level per product | Specified per-product (for overstock alerts) |
| C004 | Auto-quarantine expired batches? | Yes |
| C005 | Auto-write-off expired batches after N days? | No (requires manual confirmation) |
| C006 | Require manager approval for FEFO override? | Yes |
| C007 | Enable serial tracking for controlled substances? | Yes (regulatory requirement) |

---

## 8. Architecture Considerations

### 8.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   CLIENT LAYER (Next.js)                     │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ Dashboard│  │Inventory │  │  Sales   │  │  Reports │    │
│  │   View   │  │   View   │  │   View   │  │   View   │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
│       │              │             │             │          │
│  ┌────┴──────────────┴─────────────┴─────────────┴────┐     │
│  │              TanStack Query (Data Fetching)         │     │
│  │         + React Query DevTools (dev only)           │     │
│  └────────────────────────┬───────────────────────────┘     │
│                           │ HTTP/REST                       │
└───────────────────────────┼─────────────────────────────────┘
                            │
┌───────────────────────────┼─────────────────────────────────┐
│              API GATEWAY / BFF  (Next.js Route Handlers)    │
│         (Optional — thin proxy or aggregation layer)        │
└───────────────────────────┼─────────────────────────────────┘
                            │ HTTP/REST
┌───────────────────────────┼─────────────────────────────────┐
│                   SERVER LAYER (Spring Boot)                 │
│                                                             │
│  ┌────────────────────────────────────────────────────┐     │
│  │             REST Controllers (Adapters/Inbound)     │     │
│  │  /api/v1/products  /api/v1/batches  /api/v1/sales  │     │
│  │  /api/v1/movements /api/v1/alerts  /api/v1/reports │     │
│  └───────────────────────┬────────────────────────────┘     │
│                          │                                   │
│  ┌───────────────────────┴────────────────────────────┐     │
│  │           Application Services (Use Cases)          │     │
│  │  (Orchestrates domain logic, transactions, events)  │     │
│  └───────────────────────┬────────────────────────────┘     │
│                          │                                   │
│  ┌───────────────────────┴────────────────────────────┐     │
│  │              Domain Layer (Hexagonal Core)          │     │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐         │     │
│  │  │Aggregates│  │Domain   │  │Domain   │         │     │
│  │  │+ Entities│  │Services │  │Events   │         │     │
│  │  └──────────┘  └──────────┘  └──────────┘         │     │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐         │     │
│  │  │Value     │  │Repository│  │Ports    │         │     │
│  │  │Objects   │  │Interfaces│  │(in/out) │         │     │
│  │  └──────────┘  └──────────┘  └──────────┘         │     │
│  └───────────────────────┬────────────────────────────┘     │
│                          │                                   │
│  ┌───────────────────────┴────────────────────────────┐     │
│  │         Infrastructure / Adapters Layer             │     │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐         │     │
│  │  │JPA      │  │  Event   │  │  Email   │         │     │
│  │  │Repos    │  │Publishers│  │  Service │         │     │
│  │  └──────────┘  └──────────┘  └──────────┘         │     │
│  └───────────────────────┬────────────────────────────┘     │
│                          │                                   │
│  ┌───────────────────────┴────────────────────────────┐     │
│  │            PostgreSQL (via Supabase — plain SQL)    │     │
│  │     Flyway migrations, schema-per-context           │     │
│  └────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

### 8.2 Architectural Decisions

#### Decision 1: Hexagonal Architecture (Ports & Adapters)

**Why:** DDD + Hexagonal is the architectural style that aligns with the mandated stack and the business complexity.

**Structure:**

```
backend/
├── src/main/java/com/medistock/
│   ├── shared/                     # Shared kernel: common types, annotations, base classes
│   │   ├── domain/
│   │   │   ├── AggregateRoot.java
│   │   │   ├── DomainEvent.java
│   │   │   └── ValueObject.java
│   │   └── infrastructure/
│   │       ├── BaseEntity.java
│   │       └── BaseRepository.java
│   │
│   ├── productcatalog/             # Bounded Context: Product Catalog
│   │   ├── domain/
│   │   │   ├── Product.java
│   │   │   ├── ProductRepository.java (port)
│   │   │   └── ProductRegisteredEvent.java
│   │   ├── application/
│   │   │   ├── RegisterProductUseCase.java
│   │   │   └── UpdateProductUseCase.java
│   │   └── infrastructure/
│   │       ├── JpaProductRepository.java (adapter)
│   │       └── ProductEntity.java
│   │
│   ├── inventory/                  # Bounded Context: Inventory Management
│   │   ├── domain/
│   │   │   ├── Batch.java
│   │   │   ├── Movement.java
│   │   │   ├── FefoPickService.java (domain service)
│   │   │   ├── BatchRepository.java
│   │   │   ├── MovementRepository.java
│   │   │   └── ... events
│   │   ├── application/
│   │   │   ├── ReceiveStockUseCase.java
│   │   │   ├── RecordMovementUseCase.java
│   │   │   └── TransferBatchUseCase.java
│   │   └── infrastructure/
│   │       ├── JpaBatchRepository.java
│   │       └── JpaMovementRepository.java
│   │
│   ├── sales/                      # Bounded Context: Sales
│   │   ├── domain/
│   │   │   ├── Sale.java
│   │   │   ├── SaleItem.java
│   │   │   ├── BatchAllocation.java
│   │   │   └── SaleRepository.java
│   │   ├── application/
│   │   │   ├── CreateSaleUseCase.java (uses FefoPickService)
│   │   │   └── VoidSaleUseCase.java
│   │   └── infrastructure/
│   │       └── JpaSaleRepository.java
│   │
│   ├── alerting/                   # Bounded Context: Alerting
│   │   ├── domain/
│   │   │   ├── AlertRule.java
│   │   │   ├── AlertInstance.java
│   │   │   └── AlertRepository.java
│   │   ├── application/
│   │   │   ├── EvaluateAlertRulesUseCase.java
│   │   │   └── DismissAlertUseCase.java
│   │   └── infrastructure/
│   │       └── ...
│   │
│   └── reporting/                  # Bounded Context: Reporting (read model)
│       ├── application/
│       │   └── ...
│       └── infrastructure/
│           └── JdbcReportQueries.java  (direct SQL for complex queries)
│
├── src/main/resources/
│   ├── db/migration/               # Flyway migrations
│   │   ├── V1__create_schema_product_catalog.sql
│   │   ├── V2__create_schema_inventory.sql
│   │   ├── V3__create_schema_sales.sql
│   │   └── V4__create_schema_alerting.sql
│   └── application.yml
│
└── build.gradle
```

#### Decision 2: Monolith with Modular Boundaries (starting point)

**Why:** A monolith with strict package/module boundaries enforced by architecture tests (ArchUnit) provides the benefits of a monolith (simple deployment, no network overhead, single transaction) with a clear path to services later.

**Migration path:** If needed, each bounded context can be extracted into a separate Spring Boot service because the module boundaries are already clean (ports & adapters).

#### Decision 3: No BFF (at least initially)

**Why:** For a SaaS app used within a pharmacy/local network, the overhead of a BFF layer is not justified. Next.js can call the Spring Boot API directly. Next.js server components or route handlers can do minimal data shaping if needed.

**Exception:** If the API needs to aggregate data from multiple bounded contexts for a single view, a thin BFF layer (Next.js API route or separate service) may be added later.

#### Decision 4: CQRS-lite for Stock Projections

**Why:** Stock positions are queried far more often than they change (read-heavy). Rather than computing stock from batches on every request (looo of SUM queries), maintain a **stock projection table** updated by domain events.

**How:**
- Write side: Batch aggregate updates trigger events → event handler updates stock projection
- Read side: UI queries stock projection table directly
- This is **not full CQRS** — it's a pragmatic optimization.

**Consistency:** Eventual consistency within seconds. For a pharmacy, seconds of latency between a sale and stock update is acceptable.

#### Decision 5: Schema-per-Context (database level)

Each bounded context gets its own schema namespace:

```
medistock_product_catalog.products
medistock_product_catalog.categories
medistock_inventory.batches
medistock_inventory.movements
medistock_inventory.locations
medistock_inventory.stock_projection
medistock_sales.sales
medistock_sales.sale_items
medistock_sales.batch_allocations
medistock_alerting.alert_rules
medistock_alerting.alert_instances
```

**Why:** Enforces separation at the database level. No accidental cross-context joins in application code. Also makes future service extraction easier.

### 8.3 Technology Mapping

| Concern | Choice | Rationale |
|---------|--------|-----------|
| API Architecture | RESTful JSON | Universal, well-understood, simple |
| API Documentation | SpringDoc OpenAPI | Automatic from annotations |
| Database Migrations | Flyway | Version-controlled, repeatable |
| DB Access | Spring Data JPA (entities) + JDBC (reports) | JPA for aggregates, JDBC for complex queries |
| Validation | Jakarta Validation (Bean Validation) | Standard, integrates with Spring |
| Mapping | MapStruct or manual | Avoid Lombok on entities; MapStruct for DTO mapping |
| Testing | JUnit 5 + Mockito + ArchUnit + Testcontainers | ArchUnit for architecture tests, Testcontainers for DB integration tests |
| Auth | JWT (stateless) — to be explored in Proposal | Pharmacy chains need multi-tenant auth |
| Caching | Spring Cache + Caffeine | In-process cache for read models (stock projections) |
| Scheduling | Spring @Scheduled / Quartz | For alert evaluation, batch expiry check |
| CI/CD | GitHub Actions | Build, test, lint, Docker image |

### 8.4 Clean Architecture Enforcement

**Dependency Rule:** Dependencies point INWARD. Domain layer knows nothing about infrastructure.

```
Adapters (Controllers, JPA repos) ──► Application Services ──► Domain
       ▲                                                         │
       │                                                         │
       └───────────────────── Infrastructure ────────────────────┘
```

**Enforcement via ArchUnit:**
```java
// Domain must not depend on Spring or infrastructure
layeredArchitecture()
    .layer("Domain").definedBy("..domain..")
    .layer("Application").definedBy("..application..")
    .layer("Infrastructure").definedBy("..infrastructure..")

    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
    .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
    .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer();
```

---

## 9. Risks and Complexities

### 9.1 High-Risk Areas

| Risk | Severity | Mitigation |
|------|----------|------------|
| **Concurrent FEFO picks causing oversell** | **CRITICAL** | Optimistic locking on Batch entities + retry logic. Integration test with concurrent requests. |
| **Batch expired between FEFO read and write** | **HIGH** | Re-read batch status after lock acquisition. Expired batches cannot be picked. |
| **Incorrect stock valuation due to movement sequencing** | **HIGH** | All movements are timestamped. Valuation uses point-in-time queries (no running balance). |
| **Data migration from legacy systems** | **MEDIUM** | Import API with validation. Requires careful mapping of legacy batch IDs, expiration dates, current stock. |
| **Multi-tenant data isolation** | **MEDIUM** | Tenant ID on every aggregate. Row-level security via application layer (NOT Postgres RLS, since Supabase RLS is not used). |
| **Cold chain / temperature-sensitive products** | **MEDIUM** | Location type validation. Temperature logging is out of MVP scope but must be architected for. |
| **Dual-language UI (Spanish + English)** | **LOW** | i18n in Next.js (next-intl or similar). Not a domain complexity, but an implementation detail. |
| **Reporting query performance on large datasets** | **MEDIUM** | CQRS-lite for stock projections. For chain-level reports with millions of movements, consider dedicated read replicas or periodic aggregation tables. |

### 9.2 Hardest Parts of the Domain

**1. FEFO with optimistic concurrency at scale.**
   - A busy pharmacy chain could have 10+ POS terminals all picking from the same product simultaneously.
   - The FEFO algorithm must read batches, allocate, and update atomically.
   - Solution: `SELECT ... FOR UPDATE` on the batch rows being allocated, within a single database transaction. This serializes picks per product. Acceptable at pharmacy volumes.

**2. The difference between "available stock" and "physical stock."**
   - Reserved stock (pending orders), quarantined stock, and stock in transit all complicate the simple "total quantity" picture.
   - The FEFO engine must consider only `availableQuantity = currentQuantity - reservedQuantity`.

**3. Returned stock re-entry into the FEFO chain.**
   - A returned product has a new lifecycle. Can it be resold? Only if unopened and within expiry. This requires a quality check step.
   - If restockable, the return creates a new virtual "receipt" date, which FEFO ordering naturally handles (earliest received = returned items go first among same-expiry items).

**4. Audit trail completeness vs. storage cost.**
   - Every movement is permanently stored. A chain pharmacy could generate millions of movements per year.
   - The movement table must be append-only with appropriate indexing and potentially partitioned by month/year.

**5. Regulatory compliance across different jurisdictions.**
   - Different countries/states have different rules for controlled substances, cold chain documentation, disposal documentation.
   - The domain model must accommodate extensibility: configurable compliance rules per tenant.

**6. The "negative stock" temptation.**
   - Some POS systems allow negative stock (sell now, fix inventory later). This is **unacceptable** for pharmaceuticals.
   - The system must strictly enforce non-negative stock. If a sale can't be fulfilled, it must be rejected — no soft errors, no overrides bypassing audit.

### 9.3 Technical Debt to Avoid

- ❌ Stored procedures or triggers for business logic (mandate violation)
- ❌ Direct batch quantity updates without Movement records
- ❌ Cascade deletes on aggregate relationships
- ❌ Orphan stock (stock not attributed to any batch)
- ❌ Mixing read models with write models (stock as an aggregate)
- ❌ Lombok `@Data` on JPA entities (bidirectional relationships break)
- ❌ Lazy loading in serialization (N+1 problem)
- ❌ Infinite scroll without cursor-based pagination for movements

---

## 10. UI Design Mapping

### 10.1 Screen-to-Context Mapping

| UI Screen | Bounded Context | Primary Aggregates | API Endpoints |
|-----------|----------------|-------------------|---------------|
| **Dashboard** (overview cards) | All | StockProjection, AlertInstance | `GET /api/v1/stock/summary`, `GET /api/v1/alerts/active` |
| **Product Inventory** (card grid) | Product Catalog + Inventory | Product, Batch (via StockProjection) | `GET /api/v1/products?page=...&search=...` (includes stock summary per product) |
| **Product Detail** | Product Catalog + Inventory | Product, Batch[], Movement[] | `GET /api/v1/products/{id}`, `GET /api/v1/products/{id}/batches`, `GET /api/v1/products/{id}/movements` |
| **Batch Detail** | Inventory | Batch, Movement[] | `GET /api/v1/batches/{id}`, `GET /api/v1/batches/{id}/movements` |
| **Receive Stock** | Inventory | Batch, Movement | `POST /api/v1/batches/receive` |
| **New Sale / POS** | Sales + Inventory | Sale, Batch (FEFO pick) | `POST /api/v1/sales`, `GET /api/v1/products/{id}/available-batches` |
| **Alerts Center** | Alerting | AlertInstance, AlertRule | `GET /api/v1/alerts`, `PUT /api/v1/alerts/{id}/dismiss` |
| **Reports** | Reporting | (various read models) | `GET /api/v1/reports/...` |
| **Settings** | Product Catalog + Alerting | AlertRule, Product (thresholds) | `PUT /api/v1/alert-rules/{id}`, `PUT /api/v1/products/{id}/thresholds` |

### 10.2 Product Card Data Requirements

Per the UI design reference, each product card must show:

| Card Field | Data Source | API |
|-----------|-------------|-----|
| Image | Product.imageUrl | Included in product response |
| Product Name | Product.name | Included in product response |
| SKU | Product.sku | Included in product response |
| Current Stock | StockProjection.totalQuantity | Joined via `GET /api/v1/products?includeStock=true` |
| Next Expiration Date | StockProjection.nextExpirationDate | Computed from active batches |
| Status Badge | Computed from StockProjection.status | `IN_STOCK | LOW_STOCK | OUT_OF_STOCK | OVERSTOCKED` |

**Performance:** The product list endpoint must return cards with stock data in a single query (no N+1). Use a DB view or Spring Data projection:

```sql
SELECT p.id, p.sku, p.name, p.image_url,
       COALESCE(sp.total_quantity, 0) AS current_stock,
       sp.next_expiration_date,
       sp.status
FROM medistock_product_catalog.products p
LEFT JOIN medistock_inventory.stock_projection sp ON sp.product_id = p.id
WHERE p.tenant_id = :tenantId
  AND p.is_active = true
  AND (:search IS NULL OR p.name ILIKE '%' || :search || '%' OR p.sku ILIKE '%' || :search || '%')
ORDER BY p.name ASC
OFFSET :offset LIMIT :limit
```

### 10.3 Premium SaaS UX Considerations

The design aesthetic (Linear, Stripe, Vercel) drives these architectural decisions:

- **Fast product search:** Use PostgreSQL `ILIKE` or `pg_trgm` for fuzzy search. Consider materialized search index for speed.
- **Real-time stock updates:** After a sale, the product card should reflect updated stock without full page reload. TanStack Query with `staleTime` and `refetchInterval` (short polling) or WebSocket for live updates.
- **Optimistic UI for sales:** Show updated stock immediately on the card, reconcile with server response.
- **Smooth pagination:** Cursor-based pagination for infinite scroll on product grid (not offset-based, which is slow on large datasets).
- **Status badges with animation:** Framer Motion for badge transitions (e.g., `IN_STOCK → LOW_STOCK` with a subtle pulse).

---

## Exploration Summary

### Ready for Proposal

**Yes** — This exploration has sufficient depth to proceed to the SDD Proposal phase. The domain is well-understood, the aggregates are identified, the bounded contexts are clear, and the architecture approach is grounded in the mandated stack.

### What the Proposal Should Address

1. **FEFO transaction boundaries** — exact locking strategy for concurrent picks
2. **Stock projection update strategy** — event-driven vs. batch update
3. **Dot matrix vs. modular monolith** — confirm monolith with module boundaries
4. **Auth model** — JWT, multi-tenant, role-based access
5. **API design approach** — REST or GraphQL? (REST recommended)
6. **Error handling strategy** — domain exceptions to HTTP status mapping
7. **Testing strategy** — unit, integration, E2E boundaries
8. **Phased delivery plan** — which contexts to build first

### Key Constraints Going Forward

- ALL business logic in Spring Boot domain layer (mandate)
- No stored procedures, no database-level logic (mandate)
- Supabase PostgreSQL as plain Postgres provider only (mandate)
- FEFO is not optional — it is the core differentiator
- Every movement is tracked and immutable
- Greenfield — no legacy migration concerns for MVP

---

*End of Domain Exploration — MediStock*
