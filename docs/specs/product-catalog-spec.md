# Product Catalog — Specification

> **Phase:** SDD Spec
> **Context:** Product Catalog (Bounded Context)
> **Date:** 2026-06-18
> **Status:** Draft — ready for design phase
> **Schema:** `product_catalog`

---

## Table of Contents

1. [Purpose](#1-purpose)
2. [Domain Model](#2-domain-model)
3. [Invariants & Business Rules](#3-invariants--business-rules)
4. [REST API Specification](#4-rest-api-specification)
5. [Query Specifications](#5-query-specifications)
6. [Validation Rules](#6-validation-rules)
7. [Domain Events](#7-domain-events)
8. [Error Handling](#8-error-handling)
9. [Scenarios](#9-scenarios)
10. [Frontend Specifications](#10-frontend-specifications)
11. [Testing Requirements](#11-testing-requirements)

---

## 1. Purpose

The Product Catalog context manages the pharmaceutical product master data. It is the **foundational bounded context** — Inventory, Sales, and Alerting all reference products from this context. No other context can create or modify product definitions.

**Schema namespace:** `product_catalog` (schema-per-context, NOT per-tenant).

**Multi-tenancy:** Shared database + shared schema. `tenant_id` on every business table. Application-layer isolation. `tenant_id` is inferred from the JWT token, never from request body.

### In Scope (this spec)

- Product aggregate (CRUD + lifecycle: create, update, deactivate, reactivate)
- Category (lightweight entity)
- SKU uniqueness enforcement (per tenant)
- REST API for CRUD operations
- Frontend: product card grid, product detail page, product create/edit form
- Domain events: ProductRegistered, ProductUpdated, ProductDeactivated, ProductReactivated

### Out of Scope (handled by other contexts)

- Stock positions, current stock quantity (Inventory context)
- Batch tracking, expiration management (Inventory context)
- Alert thresholds enforcement (Alerting context)
- Stock data on product cards (Inventory context provides via separate endpoint)

### Cross-Context Dependency

The UI design (see `docs/ui-design-system.md`) specifies product cards MUST display `currentStock`, `nextExpirationDate`, and `statusBadge`. These fields are NOT part of the Product Catalog context — the frontend SHALL hydrate them from the Inventory context's stock projection endpoint at render time. This is an intentional separation of concerns: Product Catalog owns product definitions, Inventory owns stock facts.

---

## 2. Domain Model

### 2.1 Product Aggregate Root

```
Product (AGGREGATE ROOT)
├── productId: UUID             (PK, generated)
├── tenantId: string            (NOT optional — multi-tenant enforcement, inferred from JWT)
├── sku: string                 (unique per tenant, immutable after creation, business key)
├── name: string                (required, max 200 chars)
├── description: string         (optional, max 2000 chars)
├── laboratory: string          (manufacturer name, required, max 200 chars)
├── presentation: string        (e.g. "30 tablets", "100ml bottle", required, max 100 chars)
├── gtin: string                (optional, 13 digits with Luhn check, global barcode)
├── categoryId: UUID            (reference to Category, required)
├── requiresSerialTracking: boolean   (default: false — for future controlled substances)
├── requiresColdChain: boolean         (default: false — for future cold chain)
├── isControlledSubstance: boolean     (default: false — for future regulatory)
├── defaultReorderPoint: int           (optional, ≥ 0, for alerting context)
├── defaultMaxStock: int               (optional, ≥ 0, must be > reorderPoint if both set)
├── isActive: boolean                  (default: true — soft delete)
├── createdAt: timestamp               (set on creation, immutable)
└── updatedAt: timestamp               (updated on every modification)

> **Non-domain fields:** `imageUrl` (presentation metadata) and `version` (optimistic locking) are managed at the infrastructure/persistence layer only — see design decisions PC-DEC-08 and PC-DEC-09.
```

### 2.2 Category (Lightweight Entity)

```
Category
├── categoryId: UUID            (PK, generated)
├── tenantId: string            (NOT optional)
├── name: string                (required, max 100 chars, e.g. "Analgesics", "Antibiotics")
├── description: string         (optional, max 500 chars)
├── isActive: boolean           (default: true)
├── createdAt: timestamp
└── updatedAt: timestamp
```

**Design decision:** Category is implemented as a lightweight entity (own table, own repository), NOT as a value object embedded in Product. Rationale: categories are shared across products, can be managed independently, and may grow attributes (e.g., regulatory flags requiring special handling for certain therapeutic classes).

---

## 3. Invariants & Business Rules

| Rule ID | Rule | Enforcement Layer | Error Code |
|---------|------|-------------------|------------|
| PC-R01 | SKU MUST be unique per tenant | DB unique constraint on `(tenant_id, sku)` | DUPLICATE_SKU |
| PC-R02 | Product name MUST NOT be blank | Jakarta Validation `@NotBlank` | INVALID_NAME |
| PC-R03 | GTIN/EAN MUST be valid format if provided (13 digits, Luhn check) | Application-level validation | INVALID_GTIN |
| PC-R04 | Products SHALL NOT be hard-deleted — soft delete via `isActive = false` | Application policy (no DELETE SQL) | — |
| PC-R05 | Laboratory name MUST NOT be blank | Jakarta Validation `@NotBlank` | INVALID_LABORATORY |
| PC-R06 | Category reference MUST exist | Foreign key validation | INVALID_CATEGORY |
| PC-R07 | A product MUST NOT be created without a tenant_id | Multi-tenant enforcement at repository level | TENANT_REQUIRED |
| PC-R08 | SKU MUST NOT change after initial creation | Immutable field in update use case | SKU_IMMUTABLE |
| PC-R09 | Deactivated products MUST NOT be referenced in new stock receipts | Application validation (Inventory context consumer) | PRODUCT_DEACTIVATED |
| PC-R10 | Presentation field MUST NOT be blank for pharmaceutical products | Jakarta Validation `@NotBlank` | INVALID_PRESENTATION |
| PC-R11 | Concurrent updates MUST be detected and rejected | JPA `@Version` at infrastructure layer. Application service catches `ObjectOptimisticLockingFailureException` | OPTIMISTIC_LOCK |
| PC-R12 | A product MUST have at least one category | Category reference validation | INVALID_CATEGORY |
| PC-R13 | `defaultMaxStock` MUST be greater than `defaultReorderPoint` when both are set | Application-level validation | INVALID_MAX_STOCK |
| PC-R14 | `defaultReorderPoint` and `defaultMaxStock` MUST be ≥ 0 when provided | Jakarta Validation `@PositiveOrZero` | INVALID_REORDER_POINT / INVALID_MAX_STOCK |
| PC-R15 | Category name MUST be unique per tenant | DB unique constraint on `(tenant_id, name)` | DUPLICATE_CATEGORY |

---

## 4. REST API Specification

### 4.1 Products API

Base path: `/api/v1/products`

| Method | Path | Description | Auth Required | Role |
|--------|------|-------------|---------------|------|
| GET | `/api/v1/products` | List products (paginated, filterable) | JWT | Any authenticated user |
| GET | `/api/v1/products/{id}` | Get single product detail | JWT | Any authenticated user |
| POST | `/api/v1/products` | Create new product | JWT | Any authenticated user |
| PUT | `/api/v1/products/{id}` | Update product | JWT | Any authenticated user |
| DELETE | `/api/v1/products/{id}` | Soft-deactivate product | JWT | Manager role |
| PATCH | `/api/v1/products/{id}/reactivate` | Reactivate deactivated product | JWT | Manager role |

#### POST /api/v1/products — Request Body

```json
{
  "sku": "IBU-400-30",              // required, max 50, alphanumeric + hyphens
  "name": "Ibuprofeno 400mg",       // required, max 200
  "description": "Antiinflamatorio 400mg",  // optional, max 2000
  "laboratory": "Lab Genérico S.A.",       // required, max 200
  "presentation": "30 tablets",            // required, max 100
  "gtin": "7891234567890",                 // optional, 13 digits
  "imageUrl": "https://..."                // optional
  "categoryId": "uuid",                    // required
  "requiresSerialTracking": false,         // optional, default false
  "requiresColdChain": false,              // optional, default false
  "isControlledSubstance": false,          // optional, default false
  "defaultReorderPoint": 10,               // optional, ≥ 0
  "defaultMaxStock": 100                   // optional, ≥ 0, > reorderPoint
}
```

**Note:** `tenantId` is NOT in the request body. It is inferred from the JWT token's `tenant_id` claim.

**Non-domain fields:** `imageUrl` is present in API DTOs as presentation metadata but is NOT part of the domain aggregate. It is stored at the infrastructure/persistence level and mapped to DTOs by the repository adapter. `version` is present in responses for optimistic locking but lives only in the JPA entity — the domain aggregate is persistence-agnostic.

#### POST /api/v1/products — Response (201 Created)

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "sku": "IBU-400-30",
  "name": "Ibuprofeno 400mg",
  "description": "Antiinflamatorio 400mg",
  "laboratory": "Lab Genérico S.A.",
  "presentation": "30 tablets",
  "gtin": "7891234567890",
  "imageUrl": "https://...",
  "categoryId": "uuid",
  "categoryName": "Analgesics",
  "requiresSerialTracking": false,
  "requiresColdChain": false,
  "isControlledSubstance": false,
  "defaultReorderPoint": 10,
  "defaultMaxStock": 100,
  "isActive": true,
  "createdAt": "2026-06-18T12:00:00Z",
  "updatedAt": "2026-06-18T12:00:00Z",
  "version": 0
}
```

**Headers:** `Location: /api/v1/products/{id}`

#### PUT /api/v1/products/{id} — Request Body

Same shape as POST, except:
- `sku` MUST be omitted or match the existing value (PC-R08). If included and different → 400 `SKU_IMMUTABLE`.
- `version` MUST be included. This is an infrastructure concern (JPA `@Version`), not a domain attribute. The client sends the version it last read; the persistence layer rejects stale updates.

```json
{
  "name": "Ibuprofeno 400mg",
  "description": "Updated description",
  "laboratory": "Lab Genérico S.A.",
  "presentation": "30 tablets",
  "gtin": "7891234567890",
  "imageUrl": "https://...",
  "categoryId": "uuid",
  "defaultReorderPoint": 15,
  "defaultMaxStock": 120,
  "version": 0
}
```

#### DELETE /api/v1/products/{id} — Response (200 OK)

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "isActive": false,
  "updatedAt": "2026-06-18T12:00:00Z",
  "version": 1
}
```

**Behavior:** Sets `isActive = false`. Does NOT delete from database. Emits `ProductDeactivated` event.

#### PATCH /api/v1/products/{id}/reactivate — Response (200 OK)

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "isActive": true,
  "updatedAt": "2026-06-18T12:00:00Z",
  "version": 1
}
```

### 4.2 Categories API

Base path: `/api/v1/categories`

| Method | Path | Description | Auth Required |
|--------|------|-------------|---------------|
| GET | `/api/v1/categories` | List categories | JWT |
| POST | `/api/v1/categories` | Create category | JWT |
| PUT | `/api/v1/categories/{id}` | Update category | JWT |
| DELETE | `/api/v1/categories/{id}` | Soft-deactivate category | JWT (manager) |

#### POST /api/v1/categories — Request Body

```json
{
  "name": "Analgesics",
  "description": "Pain relief medications"
}
```

#### GET /api/v1/categories — Response

```json
[
  {
    "id": "uuid",
    "name": "Analgesics",
    "description": "Pain relief medications",
    "isActive": true,
    "productCount": 12
  }
]
```

**Note:** `productCount` is a computed field showing how many active products reference this category. Categories with active products MUST NOT be deactivated (returns 409 CONFLICT).

---

## 5. Query Specifications

### 5.1 GET /api/v1/products — Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `search` | string | No | — | Search by name or SKU, case-insensitive partial match (ILIKE) |
| `categoryId` | UUID | No | — | Filter by exact category |
| `isActive` | boolean | No | `true` | Filter by active status. Pass `null`/`all` to include both |
| `page` | int | No | `0` | Zero-indexed page number |
| `size` | int | No | `20` | Page size (max 100) |
| `sort` | string | No | `name:asc` | Sort field and direction. Format: `field:direction`. Supported fields: `name`, `sku`, `createdAt`, `updatedAt`, `laboratory` |

### 5.2 GET /api/v1/products — Paginated Response

```json
{
  "content": [
    {
      "id": "uuid",
      "sku": "IBU-400-30",
      "name": "Ibuprofeno 400mg",
      "description": "Antiinflamatorio 400mg",
      "laboratory": "Lab Genérico S.A.",
      "presentation": "30 tablets",
      "imageUrl": "https://...",
      "categoryId": "uuid",
      "categoryName": "Analgesics",
      "isActive": true,
      "createdAt": "2026-06-18T12:00:00Z"
    }
  ],
  "totalPages": 5,
  "totalElements": 83,
  "page": 0,
  "size": 20
}
```

**Product card data contract:** Each item in `content[]` includes exactly the fields the frontend needs to render a product card. Stock-related fields (`currentStock`, `nextExpirationDate`, `statusBadge`) are NOT included — they will be provided by the Inventory context's stock projection endpoint.

### 5.3 GET /api/v1/products/{id} — Single Product Response

```json
{
  "id": "uuid",
  "sku": "IBU-400-30",
  "name": "Ibuprofeno 400mg",
  "description": "Antiinflamatorio 400mg",
  "laboratory": "Lab Genérico S.A.",
  "presentation": "30 tablets",
  "gtin": "7891234567890",
  "imageUrl": "https://...",
  "categoryId": "uuid",
  "categoryName": "Analgesics",
  "requiresSerialTracking": false,
  "requiresColdChain": false,
  "isControlledSubstance": false,
  "defaultReorderPoint": 10,
  "defaultMaxStock": 100,
  "isActive": true,
  "createdAt": "2026-06-18T12:00:00Z",
  "updatedAt": "2026-06-18T12:00:00Z",
  "version": 0
}
```

---

## 6. Validation Rules

| Field | Rule | Error Code | HTTP Status |
|-------|------|-----------|-------------|
| `sku` | REQUIRED, max 50 chars, pattern: `^[A-Za-z0-9\-]+$` | `INVALID_SKU` | 400 |
| `sku` | MUST be unique per tenant | `DUPLICATE_SKU` | 409 |
| `sku` | MUST NOT be changed after creation | `SKU_IMMUTABLE` | 400 |
| `name` | REQUIRED, max 200 chars | `INVALID_NAME` | 400 |
| `laboratory` | REQUIRED, max 200 chars | `INVALID_LABORATORY` | 400 |
| `presentation` | REQUIRED, max 100 chars | `INVALID_PRESENTATION` | 400 |
| `gtin` | Optional, 13 digits, MUST pass Luhn check | `INVALID_GTIN` | 400 |
| `categoryId` | REQUIRED, MUST reference existing active category | `INVALID_CATEGORY` | 400 |
| `defaultReorderPoint` | Optional, MUST be ≥ 0 | `INVALID_REORDER_POINT` | 400 |
| `defaultMaxStock` | Optional, MUST be ≥ 0, MUST be > `defaultReorderPoint` if both set | `INVALID_MAX_STOCK` | 400 |
| `version` | REQUIRED on PUT, MUST match current DB value | `OPTIMISTIC_LOCK` | 409 |
| Category `name` | REQUIRED, max 100 chars, unique per tenant | `DUPLICATE_CATEGORY` | 409 |

---

## 7. Domain Events

All events are published synchronously within the same transaction via Spring `ApplicationEventPublisher` + `@TransactionalEventListener` (Phase 1 MVP strategy). This ensures events are only published if the transaction commits.

### 7.1 ProductRegistered

```json
{
  "eventType": "ProductRegistered",
  "productId": "uuid",
  "tenantId": "tenant-123",
  "sku": "IBU-400-30",
  "name": "Ibuprofeno 400mg",
  "categoryId": "uuid",
  "timestamp": "2026-06-18T12:00:00Z"
}
```

**Published when:** POST `/api/v1/products` succeeds (201 Created).
**Consumers:**
- **Inventory context:** Prepares for potential stock receipt of this product.
- **Alerting context:** May initialize default alert thresholds for the new product.

### 7.2 ProductUpdated

```json
{
  "eventType": "ProductUpdated",
  "productId": "uuid",
  "tenantId": "tenant-123",
  "changedFields": ["name", "defaultReorderPoint", "defaultMaxStock"],
  "timestamp": "2026-06-18T12:00:00Z"
}
```

**Published when:** PUT `/api/v1/products/{id}` succeeds (200 OK).
**Consumers:**
- **Alerting context:** If `defaultReorderPoint` or `defaultMaxStock` changed, re-evaluate alert thresholds.

### 7.3 ProductDeactivated

```json
{
  "eventType": "ProductDeactivated",
  "productId": "uuid",
  "tenantId": "tenant-123",
  "timestamp": "2026-06-18T12:00:00Z"
}
```

**Published when:** DELETE `/api/v1/products/{id}` succeeds (200 OK).
**Consumers:**
- **Inventory context:** Prevent new stock receipts for this product.
- **Alerting context:** Clear any active low-stock alerts for this product.

### 7.4 ProductReactivated

```json
{
  "eventType": "ProductReactivated",
  "productId": "uuid",
  "tenantId": "tenant-123",
  "timestamp": "2026-06-18T12:00:00Z"
}
```

**Published when:** PATCH `/api/v1/products/{id}/reactivate` succeeds (200 OK).
**Consumers:**
- **Inventory context:** Re-allow stock receipts for this product.
- **Alerting context:** Re-evaluate low-stock status.

---

## 8. Error Handling

### 8.1 Standard Error Response Format

```json
{
  "status": 400,
  "code": "INVALID_SKU",
  "message": "SKU is required and must be alphanumeric with hyphens only",
  "correlationId": "req-abc-123-def-456",
  "timestamp": "2026-06-18T12:00:00Z",
  "details": [
    {
      "field": "sku",
      "message": "must not be blank"
    }
  ]
}
```

### 8.2 Exception Mapping

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| `ProductNotFoundException` | 404 | PRODUCT_NOT_FOUND |
| `CategoryNotFoundException` | 404 | CATEGORY_NOT_FOUND |
| `DuplicateSkuException` | 409 | DUPLICATE_SKU |
| `InvalidProductDataException` | 400 | INVALID_PRODUCT_DATA |
| `ProductDeactivatedException` | 409 | PRODUCT_DEACTIVATED |
| `CategoryHasProductsException` | 409 | CATEGORY_HAS_PRODUCTS |
| `TenantMismatchException` | 403 | TENANT_MISMATCH |
| `OptimisticLockException` | 409 | OPTIMISTIC_LOCK |
| `SkuImmutableException` | 400 | SKU_IMMUTABLE |
| `SkuAlreadyExistsException` | 409 | DUPLICATE_SKU |
| `MethodArgumentNotValidException` | 400 | VALIDATION_ERROR |
| `MissingTenantException` | 403 | TENANT_REQUIRED |

### 8.3 Global Error Handling

A `@RestControllerAdvice` SHALL handle all exceptions globally. Every response MUST include:
- `correlationId` (generated per request, logged for traceability)
- `timestamp` (ISO 8601)
- Structured `details` array for field-level validation errors

---

## 9. Scenarios

### Scenario 1: Create a product successfully

```
GIVEN: Authenticated user with tenant "pharmacy-123"
  AND: Category "Analgesics" exists with ID "cat-uuid-1"
WHEN:  User POSTs to /api/v1/products with:
       {
         "sku": "IBU-400-30",
         "name": "Ibuprofeno 400mg",
         "laboratory": "Lab Genérico S.A.",
         "presentation": "30 tablets",
         "categoryId": "cat-uuid-1"
       }
THEN:  Response is 201 Created
  AND: Location header points to /api/v1/products/{new-id}
  AND: tenantId is "pharmacy-123" (inferred from JWT, NOT from body)
  AND: ProductRegistered event is published
  AND: GET /api/v1/products?page=0&size=20 returns the new product
  AND: Product.isActive is true
  AND: Product.version is 0
```

### Scenario 2: Duplicate SKU (same tenant)

```
GIVEN: Product with SKU "IBU-400-30" exists for tenant "pharmacy-123"
WHEN:  User from tenant "pharmacy-123" POSTs a new product with SKU "IBU-400-30"
THEN:  Response is 409 Conflict
  AND: Error code is DUPLICATE_SKU
  AND: Message includes "SKU 'IBU-400-30' already exists for this tenant"
```

### Scenario 3: Same SKU (different tenants) — isolated

```
GIVEN: Product with SKU "IBU-400-30" exists for tenant "pharmacy-123"
WHEN:  User from tenant "pharmacy-456" POSTs a new product with SKU "IBU-400-30"
THEN:  Product is created successfully (201 Created)
  AND: SKU uniqueness is scoped per tenant — no conflict
```

### Scenario 4: Update product with optimistic locking

```
GIVEN: Product exists
  AND: Its current JPA version is 5
WHEN:  User PUTs /api/v1/products/{id} with version = 5
THEN:  Product is updated
  AND: ProductUpdated event is published with changedFields list
  AND: Response is 200 OK
  AND: Response includes updated version = 6 (for subsequent requests)
```

```
GIVEN: Product exists with JPA version = 5
  AND: Another user has already updated it (version now = 6)
WHEN:  User PUTs /api/v1/products/{id} with version = 5 (stale)
THEN:  Response is 409 Conflict
  AND: Error code is OPTIMISTIC_LOCK
  AND: Message includes "Product was modified by another user. Refresh and retry."
```

> Optimistic locking enforcement is handled entirely at the persistence/infrastructure layer. The domain aggregate does NOT carry a `version` field. The `version` in API requests/responses is a JPA concern exposed for client-side concurrency handling.

### Scenario 5: Soft-deactivate a product

```
GIVEN: An active product exists (isActive = true)
WHEN:  User with manager role DELETEs /api/v1/products/{id}
THEN:  Product.isActive is false
  AND: ProductDeactivated event is published
  AND: Product.version is incremented
  AND: Response is 200 OK
```

```
GIVEN: A deactivated product exists
WHEN:  GET /api/v1/products (default isActive=true filter)
THEN:  The product is NOT in the response list
```

```
GIVEN: A deactivated product exists
WHEN:  GET /api/v1/products?isActive=false
THEN:  The product IS in the response list
```

### Scenario 6: Reactivate a product

```
GIVEN: A deactivated product exists (isActive = false)
WHEN:  User with manager role PATCHes /api/v1/products/{id}/reactivate
THEN:  Product.isActive is true
  AND: ProductReactivated event is published
  AND: Response is 200 OK
```

### Scenario 7: Create product with invalid GTIN

```
GIVEN: Authenticated user with valid tenant
WHEN:  User POSTs to /api/v1/products with:
       {
         "gtin": "1234"  // 4 digits instead of 13
       }
THEN:  Response is 400 Bad Request
  AND: Error code is INVALID_GTIN
  AND: details array includes field-level message
```

### Scenario 8: Update with immutable SKU

```
GIVEN: Product exists with SKU "IBU-400-30"
WHEN:  User PUTs /api/v1/products/{id} with sku = "NEW-SKU-999"
THEN:  Response is 400 Bad Request
  AND: Error code is SKU_IMMUTABLE
  AND: Message includes "SKU cannot be changed after product creation"
```

### Scenario 9: Deactivate product without manager role

```
GIVEN: Authenticated user WITHOUT manager role
WHEN:  User DELETEs /api/v1/products/{id}
THEN:  Response is 403 Forbidden
```

### Scenario 10: Create category then deactivate with products

```
GIVEN: Category exists with 5 active products referencing it
WHEN:  User DELETEs /api/v1/categories/{id}
THEN:  Response is 409 Conflict
  AND: Error code is CATEGORY_HAS_PRODUCTS
  AND: Message includes "Cannot deactivate category with 5 active products"
```

### Scenario 11: Missing tenant in JWT

```
GIVEN: Authenticated user WITHOUT tenant_id claim in JWT
WHEN:  User POSTs to /api/v1/products
THEN:  Response is 403 Forbidden
  AND: Error code is TENANT_REQUIRED
```

---

## 10. Frontend Specifications

### 10.1 Component Tree

```
pages/products/
├── page.tsx                 ← Product list page (ProductGrid + ProductSearch)
└── [id]/page.tsx            ← ProductDetailPage

components/products/
├── ProductCard.tsx          ← Single product card (presentational)
├── ProductGrid.tsx          ← Responsive grid of ProductCards
├── ProductSearch.tsx        ← Search + filter bar
├── ProductForm.tsx          ← Create/edit form
├── ProductDetail.tsx        ← Full product detail view
└── ProductDetailSkeleton.tsx ← Loading skeleton for detail page
```

### 10.2 ProductCard

**Type:** Presentational component.

**Props:**
```typescript
interface ProductSummary {
  id: string;
  sku: string;
  name: string;
  laboratory: string;
  presentation: string;
  imageUrl?: string;
  categoryName: string;
  isActive: boolean;
}
```

**Renders:**
- Product image (with fallback placeholder)
- Product name (bold, truncated to 2 lines)
- SKU (monospace, muted)
- Laboratory name
- Presentation info
- Active/inactive badge

**States:**
- **Default:** Renders full card with all data
- **Loading (skeleton):** Gray animated placeholder matching card dimensions
- **Error:** Error state with retry button (handled at grid level, not individual card)

**Design constraints:**
- Cards MUST NOT display stock data — this comes from Inventory context
- Cards MUST use rounded corners and subtle shadows per UI design system
- Cards MUST NOT be table rows

### 10.3 ProductGrid

**Type:** Layout component.

**Props:**
```typescript
interface ProductGridProps {
  products: ProductSummary[];
  isLoading: boolean;
  error: Error | null;
  onRetry: () => void;
  hasMore: boolean;
  onLoadMore: () => void;
}
```

**Renders:**
- Responsive grid: 4 columns (xl), 3 columns (lg), 2 columns (md), 1 column (sm)
- "Load more" button at bottom when `hasMore` is true
- Click on card navigates to `/products/{id}`

**States:**
- **Loading (initial):** Skeleton grid (6-8 skeleton cards)
- **Empty:** Illustration + "No products yet" + "Create your first product" CTA button
- **Error:** Error message + Retry button
- **Loading (more):** Spinner below last row + new cards appended on arrival

### 10.4 ProductSearch

**Type:** Filter bar component.

**Props:**
```typescript
interface ProductSearchProps {
  onSearch: (query: string) => void;
  onCategoryFilter: (categoryId: string | null) => void;
  onSort: (sort: string) => void;
  categories: { id: string; name: string }[];
}
```

**Renders:**
- Search input with magnifying glass icon (debounced 300ms)
- Category dropdown filter
- Sort selector (Name A-Z, Name Z-A, Newest, Oldest)
- Active filter indicators

**Behavior:**
- All filters update URL search params (shareable URLs)
- Debounced search (300ms) to avoid excessive API calls
- Clearing search reverts to unfiltered list

### 10.5 ProductForm

**Type:** Form component used for both create and edit.

**Props:**
```typescript
interface ProductFormProps {
  mode: 'create' | 'edit';
  initialData?: ProductDetail;   // required for edit
  categories: Category[];
  onSubmit: (data: ProductFormData) => Promise<void>;
  onCancel: () => void;
}
```

**Fields:**
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| name | text | Yes | — |
| sku | text | Yes | Disabled in edit mode |
| laboratory | text | Yes | — |
| presentation | text | Yes | — |
| gtin | text | No | 13-digit validation |
| categoryId | select | Yes | Dropdown of active categories |
| description | textarea | No | — |
| defaultReorderPoint | number | No | ≥ 0 |
| defaultMaxStock | number | No | ≥ 0, > reorderPoint |

**States:**
- **Pristine:** Empty form with default values (create) or pre-filled (edit)
- **Validating:** Inline field validation on blur and on change after first touch
- **Submitting:** Submit button shows spinner, all fields disabled
- **Error:** Server error banner at top + field-level errors
- **Success:** Toast notification + redirect to product detail (create) or back (edit)

### 10.6 ProductDetailPage

**Route:** `/products/[id]`

**Sections:**
- Product info header (name, SKU, active status badge)
- Attributes table (laboratory, presentation, GTIN, category)
- Configuration section (reorder point, max stock, flags)
- Metadata (created at, updated at, version)
- Action bar: Edit button, Deactivate/Reactivate toggle (manager role only)

**States:**
- **Loading:** Full-page skeleton with header + card placeholders
- **Not found:** "Product not found" illustration + back to products link
- **Error (server):** Error banner + retry button
- **Deactivated:** Warning banner at top "This product is deactivated" + reactivate button

---

## 11. Testing Requirements

Strict TDD: Domain tests MUST be written BEFORE domain implementation. Every requirement below corresponds to specific test classes.

### 11.1 Domain Unit Tests (JUnit 5)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `ProductTest` | Create product with valid data; Create product with blank name → throws; Create with blank laboratory → throws; Create with blank presentation → throws; Create with invalid GTIN → throws; Activate/deactivate lifecycle; Setting reorderPoint > maxStock → throws; SKU format validation |
| `CategoryTest` | Create category with valid data; Create with blank name → throws; Deactivate category |
| `SkuUniquenessTest` | SKU equality: same SKU, same tenant = conflict; same SKU, different tenant = allowed |

### 11.2 Application Use Case Tests (JUnit 5 + Mockito)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `RegisterProductUseCaseTest` | Success — product created, event published; Duplicate SKU → DuplicateSkuException; Invalid data → InvalidProductDataException; Tenant isolation — SKU exists in tenant A but not B |
| `UpdateProductUseCaseTest` | Success — fields updated, event published, version incremented; Optimistic lock conflict → OptimisticLockException; SKU immutable → SkuImmutableException |
| `DeactivateProductUseCaseTest` | Success — isActive=false, event published; Already deactivated — idempotent (no error); Product not found → ProductNotFoundException |
| `ReactivateProductUseCaseTest` | Success — isActive=true, event published; Already active — idempotent; Product not found → ProductNotFoundException |

### 11.3 Integration Tests (Testcontainers + SpringBootTest)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `JpaProductRepositoryTest` | CRUD operations; tenant isolation — tenant A cannot read tenant B products; Optimistic locking — concurrent update fails with version mismatch; Unique constraint on (tenant_id, sku); Soft delete — isActive=false, still in DB |
| `JpaCategoryRepositoryTest` | CRUD operations; Unique constraint on (tenant_id, name); Find categories with active product count |

### 11.4 REST API Tests (Spring MockMvc)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `ProductControllerTest` | POST 201 — valid product; POST 400 — validation errors (blank fields, invalid GTIN); POST 409 — duplicate SKU; POST 403 — missing tenant; GET 200 — paginated list with filters; GET 200 — single product by ID; GET 404 — non-existent ID; PUT 200 — successful update; PUT 409 — optimistic lock conflict; PUT 400 — SKU immutable; DELETE 200 — soft deactivate; DELETE 403 — non-manager role; PATCH 200 — reactivate |
| `CategoryControllerTest` | GET 200 — list; POST 201 — create; POST 409 — duplicate name; PUT 200 — update; DELETE 200 — soft deactivate (no products); DELETE 409 — has active products |

### 11.5 Frontend Unit Tests (Vitest + Testing Library)

| Test File | Scenarios Covered |
|-----------|------------------|
| `ProductCard.test.tsx` | Renders all fields; Shows inactive badge when isActive=false; Shows fallback image when imageUrl missing; Click navigates to detail page |
| `ProductGrid.test.tsx` | Renders grid of cards; Shows skeleton on loading; Shows empty state with CTA; Shows error with retry; Load more button when hasMore=true |
| `ProductSearch.test.tsx` | Calls onSearch with debounced value; Calls onCategoryFilter on dropdown change; Updates URL search params |
| `ProductForm.test.tsx` | Renders all fields; Shows inline validation errors; Disables SKU in edit mode; Calls onSubmit with transformed data; Shows submitting state; Shows server errors |

### 11.6 Architecture Tests (ArchUnit)

| Test Class | Rule |
|-----------|------|
| `HexagonalArchitectureTest` | Domain layer has ZERO dependencies on framework (Spring, JPA, etc.); Application layer depends ONLY on domain; Infrastructure layer depends on domain + framework but NEVER on another context's infrastructure; Controller classes are in infrastructure.rest, not in domain or application; Repository interfaces are in domain, implementations are in infrastructure.persistence |

---

## Appendix A: Database Schema (Flyway V1)

```sql
-- V1__create_schema_product_catalog.sql

CREATE SCHEMA IF NOT EXISTS product_catalog;

CREATE TABLE product_catalog.categories (
    category_id   UUID PRIMARY KEY,
    tenant_id     VARCHAR(36) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_categories_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE product_catalog.products (
    product_id                 UUID PRIMARY KEY,
    tenant_id                  VARCHAR(36) NOT NULL,
    sku                        VARCHAR(50) NOT NULL,
    name                       VARCHAR(200) NOT NULL,
    description                VARCHAR(2000),
    laboratory                 VARCHAR(200) NOT NULL,
    presentation               VARCHAR(100) NOT NULL,
    gtin                       VARCHAR(13),
    image_url                  VARCHAR(2048),
    category_id                UUID NOT NULL,
    requires_serial_tracking   BOOLEAN NOT NULL DEFAULT FALSE,
    requires_cold_chain        BOOLEAN NOT NULL DEFAULT FALSE,
    is_controlled_substance    BOOLEAN NOT NULL DEFAULT FALSE,
    default_reorder_point      INTEGER,
    default_max_stock          INTEGER,
    is_active                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    version                    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_products_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id)
        REFERENCES product_catalog.categories (category_id)
);

CREATE INDEX idx_products_tenant_active
    ON product_catalog.products (tenant_id, is_active);

CREATE INDEX idx_products_tenant_search
    ON product_catalog.products (tenant_id, LOWER(name) varchar_pattern_ops);

CREATE INDEX idx_products_tenant_category
    ON product_catalog.products (tenant_id, category_id);

CREATE INDEX idx_categories_tenant_active
    ON product_catalog.categories (tenant_id, is_active);
```

## Appendix B: OpenAPI / Swagger

API documentation SHALL be auto-generated via SpringDoc OpenAPI annotations on controller classes. The spec document serves as the authoritative reference for manual review.

## Appendix C: Design Decisions Log

| ID | Decision | Rationale | Date |
|----|----------|-----------|------|
| PC-DEC-01 | Category as entity, not value object | Shared across products, independently manageable, may gain regulatory attributes per therapeutic class | 2026-06-18 |
| PC-DEC-02 | Stock data excluded from Product Catalog | Separation of concerns: Product Catalog owns definitions, Inventory owns stock facts. Prevents dual-write problems | 2026-06-18 |
| PC-DEC-03 | SKU immutable after creation | SKU is a business key referenced externally (purchase orders, supplier catalogs). Changing it breaks traceability | 2026-06-18 |
| PC-DEC-04 | Soft delete only (isActive) | Products are referenced by batches, movements, sales. Hard delete would orphan these references and break audit trail | 2026-06-18 |
| PC-DEC-05 | tenant_id inferred from JWT, never from body | Prevents tenant spoofing. Tenant isolation is infrastructure-level enforcement, not user-controlled | 2026-06-18 |
| PC-DEC-06 | Category deactivation blocked when products exist | Prevents orphaned product references. Products must be recategorized first | 2026-06-18 |
| PC-DEC-07 | Paginated API with "Load more" (not infinite scroll) for MVP | Simpler state management, works with TanStack Query's paginated queries, accessible (no scroll trap) | 2026-06-18 |
| PC-DEC-08 | `imageUrl` removed from domain aggregate, kept in API DTOs and JPA entity | `imageUrl` is presentation metadata — it does not participate in any business rule or invariant. The domain model should not know about image URLs. Infrastructure layer handles storage and mapping | 2026-06-18 |
| PC-DEC-09 | `version` (optimistic locking) removed from domain aggregate, kept in JPA entity only | The domain aggregate must be persistence-agnostic. `@Version` is a JPA infrastructure concern. The repository implementation detects conflicts and throws `OptimisticLockException`. The API exposes version as a client-side concurrency token only | 2026-06-18 |
