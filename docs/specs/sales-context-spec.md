# Sales Context — Specification

> **Phase:** SDD Spec
> **Context:** Sales (Bounded Context)
> **Date:** 2026-06-18
> **Status:** Draft — ready for design phase
> **Schema:** `sales`

---

## Table of Contents

1. [Purpose](#1-purpose)
2. [Domain Model](#2-domain-model)
3. [Inventory Design Validation](#3-inventory-design-validation)
4. [REST API Specification](#4-rest-api-specification)
5. [Domain Events](#5-domain-events)
6. [Key Scenarios](#6-key-scenarios)
7. [Frontend Specifications](#7-frontend-specifications)
8. [Testing Requirements](#8-testing-requirements)
9. [Database Schema (Flyway V3)](#9-database-schema-flyway-v3)
10. [Error Handling](#10-error-handling)
11. [Design Decisions Log](#11-design-decisions-log)

---

## 1. Purpose

The Sales context records sales transactions with FEFO-compliant batch allocation. It is the **first consumer** of the Inventory context's FEFO engine — every sale triggers batch allocation, OUT movements, and stock deduction within a single atomic transaction. The Sales context validates that the Inventory design is workable from an end-to-end business workflow perspective.

**Schema namespace:** `sales` (schema-per-context).

**Multi-tenancy:** Shared database + shared schema. `tenant_id` on every business table. Application-layer isolation. `tenant_id` is inferred from the JWT token, never from request body.

### In Scope (this spec)

- Sale aggregate (Sale + SaleItem + BatchAllocation)
- Sale creation with FEFO batch allocation (calls Inventory's `BatchAllocator`)
- Sale voiding with stock reversal
- Price snapshot at time of sale
- Movement creation (OUT movements per batch allocation, delegated to `BatchAllocator`)
- Domain events (SaleCompleted, SaleVoided)
- REST API contracts
- Frontend components (sale entry form, sales list, sale detail)
- End-to-end validation that Inventory design is workable
- Flyway V3 migration
- Full TDD strategy

### Out of Scope (this spec)

- POS integration
- Prescription number tracking
- Customer management
- Payment processing
- Tax calculation (simplified for MVP — total = subtotal)
- Discounts / promotions

### Cross-Context Dependencies

**Depends on:**
- **Inventory context** — `BatchAllocator.allocate()` for FEFO picking, movement creation
- **Product Catalog context** — product existence and price snapshots

**Consumed by:**
- **Reporting context** — reads sale records and movements for audit/valuation

---

## 2. Domain Model

### 2.1 Sale Aggregate Root

```
Sale (AGGREGATE ROOT)
├── saleId: UUID                 (PK, generated)
├── tenantId: string             (multi-tenant, inferred from JWT)
├── saleDate: LocalDateTime      (when the sale was completed)
├── saleType: SaleType           (MANUAL_ENTRY for MVP — POS_INTEGRATION is future)
├── items: List<SaleItem>        (≥ 1 item)
├── subtotal: BigDecimal         (sum of item totalPrices)
├── total: BigDecimal            (subtotal — no tax/discount for MVP)
├── performedBy: string          (userId from JWT)
├── status: SaleStatus           (COMPLETED | VOIDED)
├── voidedAt: LocalDateTime      (nullable, set when voided)
├── voidReason: string           (nullable, required when voiding)
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

**SaleType enum:**
| Value | Description |
|-------|-------------|
| `MANUAL_ENTRY` | MVP — manually entered sale via UI |
| `POS_INTEGRATION` | Future — integrated with POS system |

**SaleStatus enum:**
| Value | Description |
|-------|-------------|
| `COMPLETED` | Sale processed, stock deducted |
| `VOIDED` | Sale reversed, stock restored through reverse movements |

### 2.2 SaleItem Value Object

```
SaleItem (VALUE OBJECT — part of Sale aggregate)
├── itemId: UUID                 (generated, unique within sale)
├── productId: UUID              (reference to Product)
├── productName: string          (snapshot at time of sale)
├── productSku: string           (snapshot at time of sale)
├── quantity: int                (> 0)
├── unitPrice: BigDecimal        (price AT TIME OF SALE — snapshot)
├── totalPrice: BigDecimal       (unitPrice × quantity)
├── batchAllocations: List<BatchAllocation>   (FEFO-allocated batches)
```

### 2.3 BatchAllocation Value Object

```
BatchAllocation (VALUE OBJECT — part of SaleItem)
├── batchId: UUID                (reference to Inventory batch)
├── lotNumber: string            (snapshot)
├── expirationDate: LocalDate    (snapshot)
├── allocatedQuantity: int       (> 0, ≤ batch.currentQuantity at time of allocation)
├── costPerUnit: BigDecimal      (batch cost at time of sale — for COGS)
```

### 2.4 Business Rules

| Rule ID | Rule | Enforcement |
|---------|------|-------------|
| SAL-R01 | A sale MUST contain at least one item | Domain validation |
| SAL-R02 | All items MUST reference existing active products | Validation against Product Catalog |
| SAL-R03 | Sale quantity per item MUST be > 0 | Domain validation (Jakarta `@Positive`) |
| SAL-R04 | FEFO allocation is MANDATORY — no manual batch picking for MVP | `CreateSaleUseCase` MUST call `BatchAllocator.allocate()` |
| SAL-R05 | A sale MUST NOT be created if any product has insufficient stock | `BatchAllocator` throws `InsufficientStockException` |
| SAL-R06 | `unitPrice` is a SNAPSHOT captured at sale time — client submits, server records | Price recorded as-submitted |
| SAL-R07 | Sum of batch allocations MUST equal item quantity | Validation on `Sale` creation |
| SAL-R08 | A VOIDED sale MUST create reverse movements to restore stock | Use case creates IN movements per original OUT movement |
| SAL-R09 | A VOIDED sale MUST NOT be voided again (idempotent) | Status check before voiding |
| SAL-R10 | Original sale and movements remain in audit log after void — nothing is deleted | `status` changes to `VOIDED`, movements stay |
| SAL-R11 | Sale total = sum of item totals (no tax/discount for MVP) | Domain invariant |
| SAL-R12 | `productName` and `productSku` are snapshots, NOT live references | Set from Product at sale creation |
| SAL-R13 | Sale creation + batch allocation + movement creation MUST share a single `@Transactional` boundary | `CreateSaleUseCase` method-level `@Transactional` |

### 2.5 Workflow: Create Sale with FEFO Allocation

```
CreateSale(Request):
  │
  1. Validate all product IDs exist and are active
  2. Read current product names, SKUs, and prices (snapshot)
  3. FOR each product in the sale:
  │    │
  │    ├─ Call InventoryContext.BatchAllocator.allocate(productId, quantity)
  │    │    │
  │    │    ├─ FefoAllocationStrategy picks batches
  │    │    ├─ Creates OUT movements for each batch
  │    │    ├─ Decrements batch quantities (optimistic lock)
  │    │    └─ Returns AllocationResult{ allocations[], movementIds[] }
  │    │
  │    └─ IF InsufficientStockException → rollback entire sale
  │
  4. Create Sale aggregate with all items and batch allocations
  5. Persist Sale (within same @Transactional boundary)
  6. Publish SaleCompletedEvent
  │
  └─ Response: SaleCreated with full detail
```

**⚠️ Transactional criticality:** Steps 3–5 SHARE a single `@Transactional` method. If product 1 allocates successfully but product 2 fails, ALL batch updates and movements from product 1 MUST roll back. The `BatchAllocator.allocate()` calls are within the same transaction.

### 2.6 Workflow: Void Sale

```
VoidSale(saleId, reason):
  │
  1. Load existing Sale
  2. Validate status == COMPLETED (cannot void already-voided sale)
  3. FOR each BatchAllocation in each SaleItem:
  │    │
  │    ├─ Create IN Movement for the same batch, same quantity
  │    ├─ Increment batch.currentQuantity (optimistic lock)
  │    └─ (Same BatchAllocator pattern, reversed direction)
  │
  4. Update Sale: status = VOIDED, voidedAt = now, voidReason = reason
  5. Publish SaleVoidedEvent
  │
  └─ Response: Sale with status=VOIDED
```

---

## 3. Inventory Design Validation

The Sales spec explicitly validates that the Inventory context design is workable. Five cross-context checks are documented below.

### Check 1: BatchAllocator Contract — ✅ PASSES

**Contract tested:**
```
Sales calls: BatchAllocator.allocate(productId, requestedQuantity)
Expects return: AllocationResult { allocations: List<BatchAllocation>, movementIds: List<UUID> }
Expects side effect: OUT movements created, batch quantities decremented
Error case: InsufficientStockException → caller must handle
```

**Validation result:** ✅ **PASSES.** The `BatchAllocator` is an application service in the Inventory context with a clean input/output contract. Sales calls it once per product in the sale. The `@Transactional` boundary ensures atomicity across all products — if one product fails, all prior allocations roll back.

**Open question for design phase:** Should `BatchAllocator.allocate()` accept a single product+quantity pair, or should Sales batch all products in one call? Current design: one call per product. This is simpler but means N calls for N products in the sale. Design phase should evaluate a batch variant `allocate(Map<ProductId, Integer>)` if N > 5 becomes common.

### Check 2: Transactional Boundary — ✅ PASSES

**Contract tested:**
```
Who owns the transaction? Spring @Transactional on CreateSaleUseCase
Which repositories participate?
  - BatchRepository (Inventory context) — update batch quantities
  - MovementRepository (Inventory context) — create OUT movements
  - SaleRepository (Sales context) — create sale
All within one transaction → one unit of work
```

**Validation result:** ✅ **PASSES.** All repository beans are in the same Spring application context (modular monolith). `@Transactional` on `CreateSaleUseCase` wraps all calls. If any allocation fails, `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` or a thrown exception triggers full rollback.

**⚠️ Watch:** The `BatchAllocator.allocate()` method also has its own `@Transactional`. When called from within an existing transaction, Spring's `PROPAGATION_REQUIRED` (default) joins the caller's transaction — so the outer transaction governs atomicity. Design phase MUST verify no `@Transactional(propagation = REQUIRES_NEW)` is accidentally set on `BatchAllocator.allocate()`.

### Check 3: Price Snapshot — ✅ PASSES

**Contract tested:**
```
When is price read? At sale creation time, from Product Catalog
Is it live or snapshot? Snapshot. The unitPrice submitted by the client is recorded as-is.
What if price changes after sale? Irrelevant — sale already recorded with its snapshot.
```

**Validation result:** ✅ **PASSES.** Simple and correct. The client reads the current price from Product Catalog at UI display time and submits it with the sale request. The server records whatever price is submitted. This avoids a race condition where the price changes between client read and server write — the client's price wins.

### Check 4: Void Stock Reversal — ✅ PASSES

**Contract tested:**
```
When voiding a sale:
  - For each original OUT movement, create an IN movement for the same batch + quantity
  - Increment batch.currentQuantity (optimistic lock)
  - Same pattern as receiving stock, just with a different movement type (RETURN)
```

**Validation result:** ✅ **PASSES.** The reverse movement creation reuses the same `Movement` aggregate pattern. The `referenceType = "SALE"` and `referenceId = saleId` connect the reverse movement to the original sale. Stock is restored atomically within the same transaction as the status update.

**⚠️ Watch:** If the original batch was subsequently written off or expired, the reverse movement still succeeds because the quantity goes back to a DEPLETED or EXPIRED batch. The `currentQuantity` on that batch is incremented; the status does NOT automatically revert to ACTIVE. Design phase should clarify: does a reverse movement to an EXPIRED batch make the batch sellable again? **Recommendation:** No — reverse movements to expired batches restore the audit trail but the batch remains EXPIRED. The stock projection reflects the increase but the batch stays non-pickable.

### Check 5: StockProjection Update — ✅ PASSES

**Contract tested:**
```
After sale: MovementRecorded event → StockProjectionUpdater decrements totalQuantity
After void: MovementRecorded event (REVERSE) → StockProjectionUpdater increments totalQuantity
```

**Validation result:** ✅ **PASSES.** Eventual consistency is acceptable. The projection is rebuilt on startup as a safety net. The `MovementRecorded` event carries direction and quantity, so the `StockProjectionUpdater` can apply the correct delta regardless of movement type.

### Summary

| Check | Result | Notes |
|-------|--------|-------|
| 1. BatchAllocator Contract | ✅ PASSES | One call per product; consider batch variant for N>5 |
| 2. Transactional Boundary | ✅ PASSES | Verify no REQUIRES_NEW on BatchAllocator |
| 3. Price Snapshot | ✅ PASSES | Client-submitted price recorded as-is |
| 4. Void Stock Reversal | ✅ PASSES | Reverse movement to expired batch stays expired |
| 5. StockProjection Update | ✅ PASSES | Eventual consistency sufficient |

---

## 4. REST API Specification

Base path: `/api/v1/sales`

| Method | Path | Description | Auth Required | Role |
|--------|------|-------------|---------------|------|
| POST | `/api/v1/sales` | Create a new sale with FEFO allocation | JWT | Any authenticated user |
| GET | `/api/v1/sales` | List sales (paginated, filterable) | JWT | Any authenticated user |
| GET | `/api/v1/sales/{id}` | Get sale detail with items and allocations | JWT | Any authenticated user |
| POST | `/api/v1/sales/{id}/void` | Void a sale (reverse stock) | JWT | Manager role |

### POST /api/v1/sales — Request

```json
{
  "saleDate": "2026-06-18T14:30:00Z",
  "items": [
    {
      "productId": "uuid",
      "quantity": 10,
      "unitPrice": 15.50
    },
    {
      "productId": "uuid-2",
      "quantity": 5,
      "unitPrice": 8.90
    }
  ],
  "notes": "Walk-in customer"
}
```

**Price snapshot rule:** `unitPrice` is provided by the client (UI reads current price from Product Catalog). The server records the submitted price as-is. The server MAY validate the price is within a configurable tolerance of the current product price (warning level), but the submitted price is what gets recorded.

### POST /api/v1/sales — Response (201 Created)

```json
{
  "id": "uuid",
  "saleDate": "2026-06-18T14:30:00Z",
  "saleType": "MANUAL_ENTRY",
  "items": [
    {
      "itemId": "uuid",
      "productId": "uuid",
      "productName": "Ibuprofeno 400mg",
      "productSku": "IBU-400-30",
      "quantity": 10,
      "unitPrice": 15.50,
      "totalPrice": 155.00,
      "batchAllocations": [
        {
          "batchId": "uuid",
          "lotNumber": "L2405A",
          "expirationDate": "2026-08-15",
          "allocatedQuantity": 10,
          "costPerUnit": 12.50
        }
      ]
    }
  ],
  "subtotal": 155.00,
  "total": 155.00,
  "status": "COMPLETED",
  "performedBy": "user-123",
  "createdAt": "2026-06-18T14:30:05Z"
}
```

### POST /api/v1/sales/{id}/void — Request

```json
{
  "reason": "Customer returned all items — allergic reaction"
}
```

### POST /api/v1/sales/{id}/void — Response (200 OK)

```json
{
  "id": "uuid",
  "status": "VOIDED",
  "voidedAt": "2026-06-19T10:00:00Z",
  "voidReason": "Customer returned all items — allergic reaction",
  "performedBy": "user-123"
}
```

### GET /api/v1/sales — Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `productId` | UUID | No | — | Filter by product |
| `status` | string | No | — | Filter by SaleStatus (`COMPLETED`, `VOIDED`) |
| `dateFrom` | datetime | No | — | Start of sale date range |
| `dateTo` | datetime | No | — | End of sale date range |
| `page` | int | No | `0` | Zero-indexed page number |
| `size` | int | No | `20` | Page size (max 100) |
| `sort` | string | No | `saleDate:desc` | Sort: `saleDate`, `createdAt` |

### GET /api/v1/sales — Paginated Response (200 OK)

```json
{
  "content": [
    {
      "id": "uuid",
      "saleDate": "2026-06-18T14:30:00Z",
      "itemCount": 2,
      "total": 155.00,
      "status": "COMPLETED",
      "performedBy": "user-123",
      "createdAt": "2026-06-18T14:30:05Z"
    }
  ],
  "totalPages": 5,
  "totalElements": 42,
  "page": 0,
  "size": 20
}
```

### GET /api/v1/sales/{id} — Response (200 OK)

Same as POST response (full detail with items and batch allocations).

---

## 5. Domain Events

All events are published synchronously within the same transaction via Spring `ApplicationEventPublisher` + `@TransactionalEventListener`.

### 5.1 SaleCompleted

```json
{
  "eventType": "SaleCompleted",
  "saleId": "uuid",
  "tenantId": "tenant-123",
  "items": [
    {
      "productId": "uuid",
      "quantity": 10,
      "unitPrice": 15.50,
      "batchAllocations": [
        { "batchId": "uuid", "quantity": 10 }
      ]
    }
  ],
  "total": 155.00,
  "performedBy": "user-123",
  "timestamp": "2026-06-18T14:30:05Z"
}
```

**Published when:** POST `/api/v1/sales` succeeds (201 Created).
**Consumers:** Inventory (stock projection update via `MovementRecorded` — already published by `BatchAllocator`), Reporting.

### 5.2 SaleVoided

```json
{
  "eventType": "SaleVoided",
  "saleId": "uuid",
  "tenantId": "tenant-123",
  "reason": "Customer returned all items — allergic reaction",
  "timestamp": "2026-06-19T10:00:00Z"
}
```

**Published when:** POST `/api/v1/sales/{id}/void` succeeds (200 OK).
**Consumers:** Inventory (stock projection update via reverse movements), Reporting.

---

## 6. Key Scenarios

### 6.1 Sale Creation

#### Scenario 1: Simple sale — single batch fully covers

```
GIVEN: Product P1 exists and is ACTIVE
  AND: Batch B1 exists for P1, qty=100, exp=2026-08-15 (ACTIVE, soonest-expiring)
WHEN:  User POSTs /api/v1/sales with { items: [{ productId: P1, quantity: 10, unitPrice: 15.50 }] }
THEN:  Response is 201 Created
  AND: Sale has 1 item with batchAllocation [B1, 10]
  AND: Batch B1.currentQuantity = 90 (decremented)
  AND: SALE OUT movement is created for B1 (quantity 10, direction OUT)
  AND: SaleCompleted event published
  AND: Sale.productName = current product name (snapshot)
  AND: Sale.productSku = current product SKU (snapshot)
```

#### Scenario 2: Sale splits across multiple batches (FEFO allocation)

```
GIVEN: Product P1 has 3 ACTIVE batches:
       | B1 | 2026-08-15 | qty=30 |
       | B2 | 2026-12-15 | qty=50 |
       | B3 | 2027-03-20 | qty=100 |
WHEN:  User POSTs /api/v1/sales with { items: [{ productId: P1, quantity: 60 }] }
THEN:  Response is 201 Created
  AND: batchAllocations = [B1:30, B2:30] (FEFO: B1 depleted first, then B2)
  AND: B1.currentQuantity = 0 (DEPLETED)
  AND: B2.currentQuantity = 20
  AND: B3 not touched
```

#### Scenario 3: Multi-product sale — each goes through FEFO independently

```
GIVEN: Product P1 has batches [B1:50, B2:50], Product P2 has batches [B3:30, B4:30]
WHEN:  User POSTs /api/v1/sales with 2 items:
       { productId: P1, quantity: 60 }, { productId: P2, quantity: 20 }
THEN:  Response is 201 Created
  AND: P1 allocations = [B1:50, B2:10] (FEFO applied to P1)
  AND: P2 allocations = [B3:20] (FEFO applied to P2 independently)
  AND: BatchAllocator called once per product
  AND: Single @Transactional covers both products
```

#### Scenario 4: Insufficient stock → 409

```
GIVEN: Product P1 has total available stock = 50
WHEN:  User POSTs /api/v1/sales with { items: [{ productId: P1, quantity: 100 }] }
THEN:  Response is 409 Conflict
  AND: Error code is INSUFFICIENT_STOCK
  AND: Message includes "Requested 100 but only 50 available"
  AND: No batch quantities were decremented (full rollback)
  AND: No sale was created
```

#### Scenario 5: Non-existent product → 404

```
GIVEN: Product ID does not exist in Product Catalog
WHEN:  User POSTs /api/v1/sales with that productId
THEN:  Response is 404 Not Found
  AND: Error code is PRODUCT_NOT_FOUND
```

#### Scenario 6: Deactivated product → 409

```
GIVEN: Product P1 exists but is DEACTIVATED (isActive = false)
WHEN:  User POSTs /api/v1/sales with productId = P1
THEN:  Response is 409 Conflict
  AND: Error code is PRODUCT_DEACTIVATED
```

#### Scenario 7: Quantity = 0 → 400

```
GIVEN: Authenticated user with valid tenant
WHEN:  User POSTs /api/v1/sales with { items: [{ productId: P1, quantity: 0 }] }
THEN:  Response is 400 Bad Request
  AND: Error code is INVALID_SALE_DATA or VALIDATION_ERROR
  AND: details includes field-level message for quantity
```

#### Scenario 8: Price snapshot — recorded as submitted

```
GIVEN: Product P1 current price in Product Catalog = 15.50
WHEN:  User POSTs /api/v1/sales with { unitPrice: 14.00 } (different from current)
THEN:  Response is 201 Created
  AND: SaleItem.unitPrice = 14.00 (client-submitted price recorded)
  AND: Product price change does NOT affect this sale
```

#### Scenario 9: Multi-item sale — one fails, ALL roll back

```
GIVEN: Product P1 has stock=100, Product P2 has stock=5
WHEN:  User POSTs /api/v1/sales with:
       { items: [{ productId: P1, quantity: 10 }, { productId: P2, quantity: 10 }] }
THEN:  P1 allocation succeeds (10 removed from batch)
  AND: P2 allocation fails with InsufficientStockException
  AND: P1's batch update is rolled back (P1.currentQuantity restored to 100)
  AND: No sale was created
  AND: No OUT movements remain in the database
```

### 6.2 Sale Voiding

#### Scenario 10: Void completed sale — stock restored

```
GIVEN: Sale S1 exists with status COMPLETED
  AND: S1 has 1 item allocating 10 units from batch B1
WHEN:  User with manager role POSTs /api/v1/sales/{id}/void with { reason: "..." }
THEN:  Response is 200 OK
  AND: Sale S1.status = VOIDED
  AND: Sale S1.voidedAt is set
  AND: Sale S1.voidReason = "..." (preserved)
  AND: RETURN IN movement created for B1, quantity 10
  AND: B1.currentQuantity = previous + 10 (restored)
  AND: SaleVoided event published
```

#### Scenario 11: Void already-voided sale → 409

```
GIVEN: Sale S1 exists with status VOIDED
WHEN:  User POSTs /api/v1/sales/{id}/void
THEN:  Response is 409 Conflict
  AND: Error code is SALE_ALREADY_VOIDED
  AND: No duplicate reverse movements are created
```

#### Scenario 12: Void without manager role → 403

```
GIVEN: Authenticated user WITHOUT manager role
  AND: Sale S1 exists with status COMPLETED
WHEN:  User POSTs /api/v1/sales/{id}/void
THEN:  Response is 403 Forbidden
```

### 6.3 FEFO Validation

#### Scenario 13: FEFO picks correctly for multi-item sale with varying expiry

```
GIVEN: Product P1 has batches: B1(exp=2026-08-15, qty=30), B2(exp=2026-12-15, qty=50)
  AND: Product P2 has batches: B3(exp=2026-06-01, qty=20), B4(exp=2026-10-01, qty=40)
WHEN:  Sale with P1(qty=40), P2(qty=30) is created
THEN:  P1: [B1:30, B2:10]  ← FEFO: soonest first
  AND: P2: [B3:20, B4:10]  ← FEFO: B3 expires before B4
```

#### Scenario 14: Concurrent sales on same batch — no oversell

```
GIVEN: Batch B1 has qty=100, version=0
WHEN:  Two concurrent sale requests allocate 60 and 60 from B1
THEN:  First sale succeeds (qty=100→40, version=0→1)
  AND: Second sale triggers optimistic lock retry
  AND: Second sale re-reads (available=40) → throws InsufficientStockException
  AND: No oversell — total allocated ≤ original 100
```

#### Scenario 15: Expired batch excluded from FEFO

```
GIVEN: Product P1 has batches:
       | B1 | exp=2026-01-01 | EXPIRED | qty=50 |
       | B2 | exp=2026-08-15 | ACTIVE  | qty=100 |
WHEN:  Sale with P1(qty=50) is created
THEN:  B2 is fully allocated (50 units)
  AND: B1 is NOT touched (excluded by FEFO query)
```

### 6.4 Query

#### Scenario 16: List sales with date range filter

```
GIVEN: Sales exist on 2026-06-17, 2026-06-18, 2026-06-19
WHEN:  User GETs /api/v1/sales?dateFrom=2026-06-18&dateTo=2026-06-18&page=0&size=20
THEN:  Response is 200 OK
  AND: content contains only sales from 2026-06-18
  AND: totalElements = count of sales on that date
```

#### Scenario 17: Sale detail shows full allocation tree

```
GIVEN: Sale S1 exists with 2 items, each with batch allocations
WHEN:  User GETs /api/v1/sales/{id}
THEN:  Response includes all items
  AND: Each item includes its batchAllocations
  AND: Each batchAllocation includes batchId, lotNumber, expirationDate, allocatedQuantity, costPerUnit
  AND: Subtotal and total are computed values
```

---

## 7. Frontend Specifications

### 7.1 Component Tree

```
components/sales/
├── SaleForm.tsx                  ← Multi-line sale entry form
├── SaleList.tsx                  ← Filterable sales table
├── SaleDetail.tsx                ← Single sale view
├── BatchAllocationPreview.tsx    ← Visual FEFO allocation preview
└── SaleFormSkeleton.tsx          ← Loading skeleton for form
```

### 7.2 SaleForm

**Type:** Multi-line form component.

**Props:**
```typescript
interface SaleFormProps {
  products: ProductSummary[];
  onSubmit: (data: SaleFormData) => Promise<void>;
  onCancel: () => void;
}

interface SaleFormData {
  items: {
    productId: string;
    quantity: number;
    unitPrice: number;
  }[];
  notes?: string;
}
```

**Fields:**
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| Product | autocomplete | Yes | Search by name/SKU |
| Quantity | number | Yes | > 0 |
| Unit Price | currency | Yes | Pre-filled from product, editable |
| Notes | textarea | No | Optional |
| Add line | button | — | Adds empty product line |
| Remove line | button | — | Removes line (min 1 line) |

**Rendering behavior:**
- Dynamic row list: each row has product, quantity, unit price
- Product search autocomplete with name, SKU, and current stock badge
- Quantity validation: > 0, integer
- Unit price pre-filled from product current price, editable
- Running subtotal and total at bottom of form
- FEFO allocation preview per line (optional, from GET `/api/v1/inventory/products/{id}/available-batches`)
- Submit and Cancel buttons

**States:**
- **Loading (init):** Skeleton form — search dropdowns disabled while fetching products
- **Pristine:** Empty form with one empty line (create) or pre-filled (if editing)
- **Validating:** Inline validation: quantity > 0, product required, unit price ≥ 0
- **Submitting:** Spinner on submit, all rows disabled
- **Success:** Toast + redirect to sale detail page
- **Error:** Error banner: "Product X has insufficient stock (requested N, available M)"

### 7.3 SaleList

**Type:** Filterable data table.

**Props:**
```typescript
interface SaleListProps {
  onSaleSelect: (saleId: string) => void;
}
```

**Columns:** Sale ID (truncated UUID), Date, Items count, Total ($), Status (badge), Performed by, Created at

**Filters:** date range (dateFrom, dateTo), status dropdown (COMPLETED, VOIDED, all), product search

**States:**
- **Loading:** Skeleton rows (6-8)
- **Empty:** "No sales yet" illustration + "Create your first sale" CTA
- **Error:** Error message + Retry
- **Data:** Rows with clickable sale IDs navigating to sale detail

### 7.4 SaleDetail

**Type:** Detail view.

**Sections:**
- Header: Sale ID, Date, Status badge (green=COMPLETED, gray=VOIDED)
- Items table: Product name & SKU, Quantity, Unit price, Total price, Batch allocations (expandable)
- Allocation detail per item: batchId, lot number, expiry date, allocated qty, cost/unit
- Total card: Subtotal, Total
- Action bar: Void button (if COMPLETED + manager role)
- Void info banner: if VOIDED, shows reason + voidedAt + "Stock restored" message

**States:**
- **Loading:** Full-page skeleton
- **Not found:** "Sale not found" + back to sales list
- **VOIDED status:** Warning banner with void reason and timestamp

### 7.5 BatchAllocationPreview

**Type:** Preview component embedded in SaleForm.

**Props:**
```typescript
interface BatchAllocationPreviewProps {
  productId: string;
  requestedQuantity: number;
  allocations?: BatchAllocation[];
  isLoading: boolean;
  error?: string;
}
```

**Rendering behavior:**
- Shows for a single product line which batches FEFO would pick
- Data source: GET `/api/v1/inventory/products/{productId}/available-batches?requestedQuantity=N`
- If `?requestedQuantity=N` is provided, shows the allocation plan (which batches, split how)
- If no `requestedQuantity`, shows available batches in FEFO order without allocation plan
- Color coding: green (earliest expiry), yellow (mid), red (expiring soon)

**States:**
- **Loading:** Small skeleton placeholder
- **Available:** Shows batch list with quantities
- **Insufficient stock:** Warning highlighting the shortfall
- **Error:** Muted error message (non-blocking — allocation is server-authoritative)

---

## 8. Testing Requirements

Strict TDD: Domain tests MUST be written BEFORE domain implementation.

### 8.1 Domain Unit Tests (JUnit 5)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `SaleTest` | Create with valid items; Create with empty items → throws; Create with quantity=0 → throws; Create in COMPLETED status; Void completed sale; Void already-voided → throws; Price snapshot recorded as submitted; Total = sum of items; Batch allocations sum = item quantity |

### 8.2 Application Tests (JUnit 5 + Mockito)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `CreateSaleUseCaseTest` | Success — BatchAllocator called once per product, event published; InsufficientStock → thrown and propagated; Product not found → 404; Product deactivated → 409; Multi-item sale — partial failure causes full rollback (verify BatchAllocator calls are in same transaction) |
| `VoidSaleUseCaseTest` | Success — reverse movements created, stock restored, event published; Already voided → 409; Not found → 404; Non-manager → 403 |

### 8.3 Integration Tests (Testcontainers + SpringBootTest)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `JpaSaleRepositoryTest` | CRUD; Tenant isolation — tenant A cannot see tenant B sales; Find with items and allocations (eager load); Cascade persist — sale + items + allocations in one transaction |
| `CreateSaleIntegrationTest` | Full flow: product exists → stock exists → sale created → batch quantity decreased → OUT movement recorded. Verify in DB that all records exist. Rollback on insufficient stock — verify no records created |
| `VoidSaleIntegrationTest` | Full void: sale exists → voided → stock restored → reverse IN movement created. Verify batch quantity restored |

### 8.4 Architecture Tests (ArchUnit)

| Rule | Enforcement |
|------|-------------|
| Sales domain does NOT import any Inventory domain class | ArchUnit |
| Sales application layer calls `BatchAllocator` via interface (inversion of control) | ArchUnit |
| Sales does NOT directly access `BatchRepository` or `MovementRepository` | ArchUnit |
| `CreateSaleUseCase` is in `application` package, not in `domain` | ArchUnit |

### 8.5 Frontend Tests (Vitest + Testing Library)

| Test File | Scenarios Covered |
|-----------|------------------|
| `SaleForm.test.tsx` | Renders form with empty line; Adds lines; Removes lines (min 1); Validates quantity > 0; Shows submitting state; Shows error for insufficient stock; Calls onSubmit with correct data; Product search autocomplete works |
| `SaleList.test.tsx` | Renders rows; Filters by status; Filters by date range; Loading skeleton; Empty state; Error with retry |
| `SaleDetail.test.tsx` | Renders items and allocations; Shows VOIDED banner with reason; Shows void button only for COMPLETED + manager role |

### 8.6 E2E Test (Playwright) — Critical Path

```
1. GIVEN: Product "Ibuprofeno 400mg" exists (via Product Catalog API)
2. WHEN:  User receives stock with 2 batches:
          Batch A: qty=50, exp=2026-08-15
          Batch B: qty=100, exp=2026-12-15
3. AND:   User views stock projection — shows total=150, batchCount=2
4. AND:   User creates sale: 80 units of Ibuprofeno
5. THEN:  Sale shows allocations: [Batch A:50, Batch B:30] (FEFO)
6. AND:   Batch A depleted (0), Batch B now 70
7. AND:   Stock projection shows total=70
8. AND:   User voids the sale
9. THEN:  Sale status = VOIDED
10. AND:  Batch A = 50, Batch B = 100 (restored)
11. AND:  Movement timeline shows: RECEIPT(A) + RECEIPT(B) → SALE(A:50, B:30) → RETURN(A:50, B:30)
```

---

## 9. Database Schema (Flyway V3)

```sql
-- V3__create_schema_sales.sql

CREATE SCHEMA IF NOT EXISTS sales;

CREATE TABLE sales.sales (
    sale_id       UUID PRIMARY KEY,
    tenant_id     VARCHAR(36) NOT NULL,
    sale_date     TIMESTAMP WITH TIME ZONE NOT NULL,
    sale_type     VARCHAR(20) NOT NULL DEFAULT 'MANUAL_ENTRY',
    subtotal      DECIMAL(12,2) NOT NULL,
    total         DECIMAL(12,2) NOT NULL,
    performed_by  VARCHAR(100) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    voided_at     TIMESTAMP WITH TIME ZONE,
    void_reason   TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_sales_tenant_date
    ON sales.sales (tenant_id, sale_date DESC);
CREATE INDEX idx_sales_tenant_status
    ON sales.sales (tenant_id, status);

CREATE TABLE sales.sale_items (
    item_id       UUID PRIMARY KEY,
    sale_id       UUID NOT NULL REFERENCES sales.sales(sale_id) ON DELETE CASCADE,
    tenant_id     VARCHAR(36) NOT NULL,
    product_id    UUID NOT NULL,
    product_name  VARCHAR(200) NOT NULL,
    product_sku   VARCHAR(50) NOT NULL,
    quantity      INTEGER NOT NULL CHECK (quantity > 0),
    unit_price    DECIMAL(12,2) NOT NULL CHECK (unit_price >= 0),
    total_price   DECIMAL(12,2) NOT NULL CHECK (total_price >= 0)
);

CREATE INDEX idx_sale_items_sale
    ON sales.sale_items (sale_id);
CREATE INDEX idx_sale_items_product
    ON sales.sale_items (tenant_id, product_id);

CREATE TABLE sales.batch_allocations (
    allocation_id      UUID PRIMARY KEY,
    item_id            UUID NOT NULL REFERENCES sales.sale_items(item_id) ON DELETE CASCADE,
    batch_id           UUID NOT NULL,
    lot_number         VARCHAR(100) NOT NULL,
    expiration_date    DATE NOT NULL,
    allocated_quantity INTEGER NOT NULL CHECK (allocated_quantity > 0),
    cost_per_unit      DECIMAL(12,2) NOT NULL CHECK (cost_per_unit >= 0)
);

CREATE INDEX idx_batch_allocations_item
    ON sales.batch_allocations (item_id);
CREATE INDEX idx_batch_allocations_batch
    ON sales.batch_allocations (batch_id);
```

**Note:** `tenant_id` is added to `sale_items` for direct product queries (finding all sales of a product across items without joining through `sales` table).

---

## 10. Error Handling

### 10.1 Standard Error Response Format

```json
{
  "status": 409,
  "code": "INSUFFICIENT_STOCK",
  "message": "Insufficient stock for product 'Ibuprofeno 400mg': requested 100, available 50",
  "correlationId": "req-abc-123",
  "timestamp": "2026-06-18T14:30:00Z",
  "details": [
    { "field": "items[0].quantity", "message": "Requested 100 but only 50 available" }
  ]
}
```

### 10.2 Exception Mapping

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| `SaleNotFoundException` | 404 | SALE_NOT_FOUND |
| `InsufficientStockException` | 409 | INSUFFICIENT_STOCK |
| `ProductNotFoundException` | 404 | PRODUCT_NOT_FOUND |
| `ProductDeactivatedException` | 409 | PRODUCT_DEACTIVATED |
| `InvalidSaleDataException` | 400 | INVALID_SALE_DATA |
| `SaleAlreadyVoidedException` | 409 | SALE_ALREADY_VOIDED |
| `ConcurrentPickException` | 409 | CONCURRENT_PICK |
| `TenantMismatchException` | 403 | TENANT_MISMATCH |
| `ValidationErrorException` | 400 | VALIDATION_ERROR |

---

## 11. Design Decisions Log

| ID | Decision | Rationale | Date |
|----|----------|-----------|------|
| SAL-DEC-01 | Sales calls `BatchAllocator` via application service interface (NOT domain-to-domain) | Hexagonal architecture: Sales application layer depends on Inventory application service. Domain layers remain independent. `BatchAllocator` interface in Sales application, implementation in Inventory infrastructure | 2026-06-18 |
| SAL-DEC-02 | Price snapshot submitted by client, validated by server | Client reads current price, submits it. Server records as-is. Avoids race between price read and sale commit. If price changed significantly, server MAY warn but records the submitted price | 2026-06-18 |
| SAL-DEC-03 | Single `@Transactional` for entire sale creation | Ensures atomicity across Inventory (batch updates, movements) and Sales (sale creation). `BatchAllocator.allocate()` joins existing transaction via Propagation.REQUIRED | 2026-06-18 |
| SAL-DEC-04 | No tax, no discounts, no payments for MVP | Simplification. Tax jurisdictions vary, discounts add complexity, payment processing is separate. Sales records the total as-is. Future tax module can insert between sale total and payment | 2026-06-18 |
| SAL-DEC-05 | Void creates reverse movements (not DELETE or status-only) | Audit trail immutability. Original movements stay as evidence. Restoring stock via IN movements (type RETURN) ensures full traceability | 2026-06-18 |
| SAL-DEC-06 | `productName` and `productSku` are snapshots in `SaleItem` | Sale must be reproducible exactly as it happened. If product is later renamed or SKU changes, historical sale still shows original values | 2026-06-18 |
| SAL-DEC-07 | `BatchAllocation` includes `costPerUnit` for future COGS calculation | Enables future profit reporting and inventory valuation. Captured at time of sale, not recomputed. The cost comes from the batch's `costPerUnit` at allocation time | 2026-06-18 |
| SAL-DEC-08 | `tenant_id` added to `sale_items` table | Optimizes the query pattern "find all sales containing product X" without joining through `sales` table. Denormalization justified by multi-tenant query performance | 2026-06-18 |
| SAL-DEC-09 | One call to `BatchAllocator.allocate()` per product, not batch-call per product | Simpler contract. The allocator handles splitting across batches internally. Design phase should evaluate a batch variant for multi-product sales | 2026-06-18 |
