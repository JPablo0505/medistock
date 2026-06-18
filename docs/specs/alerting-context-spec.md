# Alerting Context — Specification

> **Phase:** SDD Spec
> **Context:** Alerting (Bounded Context)
> **Date:** 2026-06-18
> **Status:** Draft — ready for design phase
> **Schema:** `alerting`

---

## Table of Contents

1. [Purpose](#1-purpose)
2. [Domain Model](#2-domain-model)
3. [Invariants & Business Rules](#3-invariants--business-rules)
4. [Alert Generation Rules](#4-alert-generation-rules)
5. [REST API Specification](#5-rest-api-specification)
6. [Dashboard Integration](#6-dashboard-integration)
7. [Domain Events](#7-domain-events)
8. [Cross-Context Integration](#8-cross-context-integration)
9. [Key Scenarios](#9-key-scenarios)
10. [Frontend Specifications](#10-frontend-specifications)
11. [Testing Requirements](#11-testing-requirements)
12. [Database Schema (Flyway V4)](#12-database-schema-flyway-v4)
13. [Error Handling](#13-error-handling)
14. [Design Decisions Log](#14-design-decisions-log)
15. [Inventory Design Validation (Cross-Check)](#15-inventory-design-validation-cross-check)

---

## 1. Purpose

The Alerting context monitors stock thresholds and expiration dates by consuming events from the Inventory and Sales contexts. It is the **last bounded context** in the core domain — it reacts to domain events, evaluates configurable alert rules, and generates/resolves alert instances. All alerts are in-app for MVP; email/push notification delivery is deferred to post-MVP.

**Schema namespace:** `alerting` (schema-per-context).

**Multi-tenancy:** Shared database + shared schema. `tenant_id` on every business table. Application-layer isolation. `tenant_id` is inferred from the JWT token, never from request body.

### In Scope (this spec)

- AlertRule aggregate (configurable alert definitions)
- AlertInstance aggregate (generated alert occurrences)
- Alert types: LOW_STOCK, NEAR_EXPIRY, EXPIRED, BATCH_QUARANTINED
- Event-driven alert evaluation (StockDepleted, StockReplenished, MovementRecorded, BatchQuarantined)
- Scheduled alert evaluation (near-expiry daily, expired batch daily)
- Alert deduplication and idempotency
- Alert lifecycle: triggered → read → resolved/dismissed
- REST API contracts
- Dashboard integration (count badges, active alerts list)
- Frontend components (alerts panel, dashboard indicators)
- Flyway V4 migration
- Full TDD strategy

### Out of Scope (this spec)

- Email/push notifications (MVP: in-app alerts only)
- SMS notifications
- Complex escalation rules
- Alert analytics/reporting
- Configurable notification channels per tenant
- WebSocket real-time push (polling-based for MVP)
- UNUSUAL_MOVEMENT alert type (post-MVP)

### Cross-Context Dependency

The Alerting context **consumes** domain events from Inventory and Product Catalog. It does NOT poll databases (except for scheduled tasks like daily near-expiry check). It **emits** its own domain events consumed by any future notification delivery infrastructure.

---

## 2. Domain Model

### 2.1 AlertRule Aggregate Root

```
AlertRule (AGGREGATE ROOT)
├── alertRuleId: UUID             (PK, generated)
├── tenantId: string              (multi-tenant, inferred from JWT)
├── name: string                  (human-readable, e.g. "Low Stock - Ibuprofeno")
├── alertType: AlertType          (LOW_STOCK | NEAR_EXPIRY | EXPIRED | BATCH_QUARANTINED)
├── conditions: JsonNode          (type-specific conditions — see below)
├── isEnabled: boolean            (default true)
├── isDefault: boolean            (default false — system-created defaults per product)
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

**AlertType enum:**

| Value | Description |
|-------|-------------|
| `LOW_STOCK` | Stock quantity falls below threshold |
| `NEAR_EXPIRY` | Batch expiration within warning window |
| `EXPIRED` | Batch has expired |
| `BATCH_QUARANTINED` | Batch has been quarantined |

**Conditions per type (stored as JSONB):**

LOW_STOCK conditions:
```json
{
  "productId": "uuid",
  "thresholdQuantity": 10,
  "evaluateOnMovement": true
}
```

- `productId`: null = applies to ALL products (global threshold)
- `thresholdQuantity`: alert when totalQuantity ≤ this value
- `evaluateOnMovement`: re-evaluate on every MovementRecorded event

NEAR_EXPIRY conditions:
```json
{
  "daysBeforeExpiry": 90
}
```

- `daysBeforeExpiry`: configurable warning window (default: 90)

EXPIRED conditions:
```json
{
  "notifyOnExpiry": true
}
```

- `notifyOnExpiry`: always true for MVP

BATCH_QUARANTINED conditions:
```json
{
  "notifyOnQuarantine": true
}
```

- `notifyOnQuarantine`: always true for MVP

### 2.2 AlertInstance Aggregate

```
AlertInstance (AGGREGATE ROOT)
├── alertInstanceId: UUID         (PK, generated)
├── tenantId: string              (multi-tenant, inferred from JWT)
├── alertRuleId: UUID             (reference to AlertRule, nullable for system-generated alerts)
├── alertType: AlertType          (denormalized from rule for query efficiency)
├── severity: AlertSeverity       (INFO | WARNING | CRITICAL)
├── productId: UUID               (nullable — null for global alerts)
├── productName: string           (snapshot at time of alert)
├── batchId: UUID                 (nullable — null for product-level alerts)
├── lotNumber: string             (snapshot, nullable)
├── message: string               (human-readable, e.g. "Stock for Ibuprofeno 400mg is low: 5 remaining")
├── currentValue: int             (the value that triggered the alert — e.g., current stock quantity)
├── thresholdValue: int           (the threshold that was crossed)
├── isRead: boolean               (default false — user has seen it)
├── isResolved: boolean           (default false — condition no longer applies)
├── triggeredAt: LocalDateTime
├── readAt: LocalDateTime         (nullable)
├── resolvedAt: LocalDateTime     (nullable)
└── resolvedBy: string            (nullable — userId or "SYSTEM")
```

**AlertSeverity enum:**

| Value | Description |
|-------|-------------|
| `INFO` | Informational (e.g., stock replenished, alert auto-resolved) |
| `WARNING` | Needs attention (e.g., near-expiry, low stock) |
| `CRITICAL` | Immediate action required (e.g., expired batch, quarantine) |

**Severity mapping by alert type:**

| Alert Type | Default Severity | Notes |
|-----------|-----------------|-------|
| LOW_STOCK | WARNING | CRITICAL if stock = 0 (OUT_OF_STOCK) |
| NEAR_EXPIRY | WARNING | Becomes CRITICAL if within 30 days |
| EXPIRED | CRITICAL | Regulatory risk — expired product cannot be sold |
| BATCH_QUARANTINED | CRITICAL | Product blocked from sale |

---

## 3. Invariants & Business Rules

### 3.1 AlertRule Rules

| Rule ID | Rule | Enforcement Layer | Error Code |
|---------|------|-------------------|------------|
| ALR-R01 | An AlertRule MUST have at least one condition | Domain validation | INVALID_ALERT_RULE |
| ALR-R02 | An AlertRule name MUST be unique per tenant | DB UNIQUE constraint on (tenant_id, name) | DUPLICATE_ALERT_RULE |
| ALR-R03 | LOW_STOCK rules are evaluated on state TRANSITION, not continuously | Event-driven evaluation (StockDepleted → create, StockReplenished → resolve) | — |
| ALR-R04 | NEAR_EXPIRY rules are evaluated DAILY (not on every event) | `@Scheduled` cron — avoids alert storms | — |
| ALR-R05 | EXPIRED rules fire ONCE per batch when it becomes EXPIRED | Event-driven (BatchExpired event), not daily | — |
| ALR-R06 | BATCH_QUARANTINED rules fire ONCE per quarantine event | Event-driven (BatchQuarantined event) | — |
| ALR-R07 | System creates default LOW_STOCK rules for new products (based on `defaultReorderPoint`) | Application service on ProductRegistered event | — |
| ALR-R08 | Rules CAN be disabled without deleting them | `isEnabled` flag | — |

### 3.2 AlertInstance Rules

| Rule ID | Rule | Enforcement Layer | Error Code |
|---------|------|-------------------|------------|
| ALR-R09 | A CRITICAL alert CANNOT be dismissed — it MUST be resolved | Application validation | CRITICAL_ALERT_CANNOT_DISMISS |
| ALR-R10 | A resolved alert CANNOT be re-opened — a new alert instance is created | Immutable once resolved | ALREADY_RESOLVED |
| ALR-R11 | NEAR_EXPIRY alerts fire AT MOST once per day per batch | Deduplication check before creation | — |
| ALR-R12 | LOW_STOCK alerts fire on state TRANSITION only | Resolved when stock exceeds threshold + buffer (default: threshold × 1.2) | — |
| ALR-R13 | EXPIRED alerts fire ONCE per batch | Deduplication — check if active alert exists for batch+type | — |
| ALR-R14 | An alert is auto-resolved by SYSTEM when the triggering condition no longer applies | StockReplenished resolves LOW_STOCK | — |

---

## 4. Alert Generation Rules

### 4.1 LOW_STOCK Alert

**Trigger:** `StockDepleted` event OR `MovementRecorded` event when stock enters LOW_STOCK range.

**Evaluation:**
```
WHEN: MovementRecorded event consumed
  FOR EACH active LOW_STOCK rule:
    Read StockProjection for the product
    IF totalQuantity <= thresholdQuantity AND no active LOW_STOCK alert → CREATE alert (WARNING)
    IF totalQuantity = 0 AND no active LOW_STOCK alert → CREATE alert (CRITICAL)
    IF totalQuantity > thresholdQuantity AND active LOW_STOCK alert exists → RESOLVE alert (SYSTEM)

WHEN: StockDepleted event consumed
  Same as above, but always CRITICAL severity
```

**Deduplication:** Check if there is an active (unresolved) LOW_STOCK alert for the same product+rule combination. If exists, do NOT create another.

### 4.2 NEAR_EXPIRY Alert

**Trigger:** Daily `@Scheduled` cron.

**Evaluation:**
```
WHEN: Scheduled (daily at 06:00)
  FOR EACH ACTIVE or NEAR_EXPIRY batch:
    daysUntilExpiry = expirationDate - today
    threshold = global config (default: 90 days)
    IF daysUntilExpiry > 0 AND daysUntilExpiry <= threshold:
      Check if alert already created TODAY for this batch
      If not → CREATE NEAR_EXPIRY alert (WARNING)
      IF daysUntilExpiry <= 30 → CHANGE severity to CRITICAL
```

**Deduplication:** Track per-batch-per-day. Use `batchId + alertType + triggerDate` to prevent duplicate creation. Do NOT rely on `isRead`/`isResolved` — the alert may have been read but the condition persists.

### 4.3 EXPIRED Alert

**Trigger:** `BatchExpired` event from Inventory context.

**Evaluation:**
```
WHEN: BatchExpired event consumed
  Check if active EXPIRED alert exists for this batch
  If not → CREATE EXPIRED alert (CRITICAL)
```

**Auto-resolution:** EXPIRED alerts are NEVER auto-resolved. The batch must be written off. When `BatchWrittenOff` event is consumed, the alert is resolved by SYSTEM.

### 4.4 BATCH_QUARANTINED Alert

**Trigger:** `BatchQuarantined` event from Inventory context.

**Evaluation:**
```
WHEN: BatchQuarantined event consumed
  Check if active BATCH_QUARANTINED alert exists for this batch
  If not → CREATE BATCH_QUARANTINED alert (CRITICAL)
```

**Auto-resolution:** When batch is written off (post-MVP: released from quarantine).

---

## 5. REST API Specification

Base path: `/api/v1/alerts`

### 5.1 Alert Instances API

| Method | Path | Description | Auth Required | Role |
|--------|------|-------------|---------------|------|
| GET | `/api/v1/alerts` | List active alerts (filterable, paginated) | JWT | Any authenticated user |
| GET | `/api/v1/alerts/summary` | Get active alert summary counts | JWT | Any authenticated user |
| GET | `/api/v1/alerts/{id}` | Get alert detail | JWT | Any authenticated user |
| POST | `/api/v1/alerts/{id}/dismiss` | Mark alert as read/dismissed | JWT | Any authenticated user |
| POST | `/api/v1/alerts/{id}/resolve` | Mark alert as resolved (condition handled) | JWT | Manager role |

#### GET /api/v1/alerts — Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `alertType` | string | No | — | Filter by AlertType |
| `severity` | string | No | — | Filter by severity |
| `productId` | UUID | No | — | Filter by product |
| `isRead` | boolean | No | — | Filter by read status |
| `isResolved` | boolean | No | `false` | Default shows unresolved only |
| `page` | int | No | `0` | Zero-indexed page number |
| `size` | int | No | `20` | Page size (max 100) |
| `sort` | string | No | `triggeredAt:desc` | Sort field and direction |

#### GET /api/v1/alerts — Response (200 OK)

```json
{
  "content": [
    {
      "id": "uuid",
      "alertType": "LOW_STOCK",
      "severity": "WARNING",
      "productId": "uuid",
      "productName": "Ibuprofeno 400mg",
      "batchId": null,
      "message": "Stock for Ibuprofeno 400mg is low: 5 remaining (threshold: 10)",
      "currentValue": 5,
      "thresholdValue": 10,
      "isRead": false,
      "isResolved": false,
      "triggeredAt": "2026-06-18T14:30:00Z"
    }
  ],
  "totalPages": 3,
  "totalElements": 42,
  "page": 0,
  "size": 20
}
```

#### GET /api/v1/alerts/summary — Response (200 OK)

```json
{
  "totalActive": 12,
  "critical": 3,
  "warning": 7,
  "info": 2,
  "byType": {
    "LOW_STOCK": 5,
    "NEAR_EXPIRY": 4,
    "EXPIRED": 1,
    "BATCH_QUARANTINED": 2
  }
}
```

#### POST /api/v1/alerts/{id}/dismiss — Response (200 OK)

```json
{
  "id": "uuid",
  "isRead": true,
  "readAt": "2026-06-18T15:00:00Z"
}
```

**Note:** Dismiss marks the alert as READ. The alert remains unresolved if the condition persists. CRITICAL alerts CANNOT be dismissed (MUST be resolved).

#### POST /api/v1/alerts/{id}/resolve — Request

```json
{
  "resolution": "Stock replenished — 500 units received",
  "resolvedBy": "user-123"
}
```

#### POST /api/v1/alerts/{id}/resolve — Response (200 OK)

```json
{
  "id": "uuid",
  "isResolved": true,
  "resolvedAt": "2026-06-19T09:00:00Z",
  "resolvedBy": "user-123"
}
```

### 5.2 Alert Rules API

| Method | Path | Description | Auth Required | Role |
|--------|------|-------------|---------------|------|
| GET | `/api/v1/alert-rules` | List alert rules | JWT | Manager role |
| POST | `/api/v1/alert-rules` | Create custom alert rule | JWT | Manager role |
| PUT | `/api/v1/alert-rules/{id}` | Update alert rule | JWT | Manager role |
| DELETE | `/api/v1/alert-rules/{id}` | Disable alert rule (soft-delete via `isEnabled`) | JWT | Manager role |

#### POST /api/v1/alert-rules — Request Body

```json
{
  "name": "Low Stock - Ibuprofeno 400mg",
  "alertType": "LOW_STOCK",
  "conditions": {
    "productId": "uuid",
    "thresholdQuantity": 10,
    "evaluateOnMovement": true
  }
}
```

#### POST /api/v1/alert-rules — Response (201 Created)

```json
{
  "id": "uuid",
  "name": "Low Stock - Ibuprofeno 400mg",
  "alertType": "LOW_STOCK",
  "conditions": {
    "productId": "uuid",
    "thresholdQuantity": 10,
    "evaluateOnMovement": true
  },
  "isEnabled": true,
  "isDefault": false,
  "createdAt": "2026-06-18T12:00:00Z",
  "updatedAt": "2026-06-18T12:00:00Z"
}
```

---

## 6. Dashboard Integration

The Alerting context provides data for the dashboard via two endpoints:

**Active alert counts (for sidebar badge + dashboard header):**
```
GET /api/v1/alerts/summary
```

**Recent critical alerts (for dashboard top section):**
```
GET /api/v1/alerts?severity=CRITICAL&isResolved=false&size=5&sort=triggeredAt:desc
```

**Dashboard widget data flow:**
1. Sidebar `<AlertBadge>` calls `GET /api/v1/alerts/summary` on mount and every 60s (polling)
2. Dashboard `<AlertSummaryRow>` calls the same endpoint
3. Dashboard "Critical Alerts" section calls `GET /api/v1/alerts?severity=CRITICAL&isResolved=false&size=5`
4. Polling interval: 60 seconds for MVP (no WebSocket)

---

## 7. Domain Events

All events are published synchronously within the same transaction via Spring `ApplicationEventPublisher` + `@TransactionalEventListener` (Phase 1 MVP strategy). Phase 2 will migrate to an outbox pattern + message broker for cross-context eventual consistency.

### 7.1 LowStockAlertTriggered

```json
{
  "eventType": "LowStockAlertTriggered",
  "alertInstanceId": "uuid",
  "productId": "uuid",
  "productName": "Ibuprofeno 400mg",
  "currentStock": 5,
  "threshold": 10,
  "severity": "WARNING",
  "tenantId": "tenant-123",
  "timestamp": "2026-06-18T14:30:00Z"
}
```

**Published when:** LOW_STOCK alert is created (new AlertInstance persisted).
**Consumers:** Future notification delivery infrastructure.

### 7.2 NearExpiryAlertTriggered

```json
{
  "eventType": "NearExpiryAlertTriggered",
  "alertInstanceId": "uuid",
  "batchId": "uuid",
  "productId": "uuid",
  "productName": "Ibuprofeno 400mg",
  "lotNumber": "L2405A",
  "daysUntilExpiry": 45,
  "severity": "WARNING",
  "tenantId": "tenant-123",
  "timestamp": "2026-06-18T06:00:00Z"
}
```

### 7.3 ExpiredAlertTriggered

```json
{
  "eventType": "ExpiredAlertTriggered",
  "alertInstanceId": "uuid",
  "batchId": "uuid",
  "productId": "uuid",
  "productName": "Ibuprofeno 400mg",
  "lotNumber": "L2405A",
  "quantity": 80,
  "severity": "CRITICAL",
  "tenantId": "tenant-123",
  "timestamp": "2026-06-18T00:00:00Z"
}
```

---

## 8. Cross-Context Integration

### 8.1 Consumed Events

| Event | Source Context | Triggers |
|-------|---------------|----------|
| `StockDepleted` | Inventory | Create LOW_STOCK alert (CRITICAL) |
| `StockReplenished` | Inventory | Resolve active LOW_STOCK alert (SYSTEM) |
| `BatchExpired` | Inventory | Create EXPIRED alert (CRITICAL) |
| `BatchNearExpiry` | Inventory | Create NEAR_EXPIRY alert (daily — consumed by scheduled job, not direct) |
| `BatchQuarantined` | Inventory | Create BATCH_QUARANTINED alert (CRITICAL) |
| `MovementRecorded` | Inventory | Trigger low-stock re-evaluation |
| `BatchWrittenOff` | Inventory | Resolve active EXPIRED / QUARANTINED alerts for that batch |
| `ProductDeactivated` | Product Catalog | Resolve active LOW_STOCK alerts for that product (SYSTEM) |
| `ProductReactivated` | Product Catalog | Re-evaluate LOW_STOCK for that product |
| `ProductRegistered` | Product Catalog | Create default LOW_STOCK rule (if product has `defaultReorderPoint`) |
| `ProductUpdated` | Product Catalog | Update default LOW_STOCK rule threshold if `defaultReorderPoint` changed |
| `SaleCompleted` | Sales | Re-evaluate low stock (via MovementRecorded — already published by Inventory) |

### 8.2 Event Consumer Architecture

```
Inventory events ──► @TransactionalEventListener ──► AlertEvaluationService
                                  │
                    ┌─────────────┴─────────────┐
                    ▼                           ▼
        AlertInstance created         AlertInstance resolved
                    │                           │
                    ▼                           ▼
        LowStockAlertTriggered        AlertResolvedEvent (internal)
        / NearExpiryAlertTriggered
        / ExpiredAlertTriggered
```

### 8.3 Scheduled Tasks

| Task | Schedule | Description |
|------|----------|-------------|
| `evaluateNearExpiryAlerts` | Daily at 06:00 | Check all ACTIVE/NEAR_EXPIRY batches against NEAR_EXPIRY rules |
| `evaluateExpiredBatches` | Daily at 00:00 | Check for newly expired batches (as safety net — primary trigger is BatchExpired event) |

---

## 9. Key Scenarios

### 9.1 LOW_STOCK Alerts

#### Scenario 1: Stock drops below threshold → LOW_STOCK alert created (WARNING)

```
GIVEN: Product P1 has a LOW_STOCK rule with thresholdQuantity = 10
  AND: Product P1 has StockProjection.totalQuantity = 15
  AND: No active LOW_STOCK alert exists for P1
WHEN:  MovementRecorded event is consumed (OUT movement, quantity = 6)
  AND: StockProjection.totalQuantity becomes 9
THEN:  A LOW_STOCK alert is created (severity = WARNING)
  AND: alert.currentValue = 9, alert.thresholdValue = 10
  AND: alert.productId = P1's ID
  AND: LowStockAlertTriggered event is published
```

#### Scenario 2: Stock reaches 0 → LOW_STOCK alert created (CRITICAL)

```
GIVEN: Product P1 has a LOW_STOCK rule with thresholdQuantity = 10
  AND: StockProjection.totalQuantity = 0 (all batches depleted)
  AND: No active LOW_STOCK alert exists for P1
WHEN:  StockDepleted event is consumed for P1
THEN:  A LOW_STOCK alert is created (severity = CRITICAL)
  AND: alert.currentValue = 0, alert.thresholdValue = 10
  AND: message indicates "out of stock"
```

#### Scenario 3: Stock replenished above threshold → LOW_STOCK alert auto-resolved

```
GIVEN: Product P1 has active LOW_STOCK alert (severity = WARNING, isResolved = false)
  AND: StockProjection.totalQuantity = 9 (below threshold of 10)
WHEN:  StockReplenished event is consumed (new quantity = 50)
  AND: totalQuantity (50) > thresholdQuantity (10)
THEN:  The active LOW_STOCK alert is resolved (isResolved = true, resolvedBy = "SYSTEM")
  AND: resolvedAt is set
```

#### Scenario 4: Multiple movements below threshold → only ONE alert created (deduplication)

```
GIVEN: Product P1 has LOW_STOCK rule with thresholdQuantity = 10
  AND: StockProjection.totalQuantity = 8 (below threshold)
WHEN:  MovementRecorded event is consumed (OUT, quantity = 1, totalQuantity becomes 7)
  AND: MovementRecorded event is consumed again (OUT, quantity = 1, totalQuantity becomes 6)
THEN:  Only ONE LOW_STOCK alert was created (on first transition into LOW_STOCK range)
  AND: The second movement did NOT create a duplicate alert
```

#### Scenario 5: LOW_STOCK alert dismissed — stock still low, original remains unresolved

```
GIVEN: Active LOW_STOCK alert exists for P1 (isRead = false, isResolved = false)
  AND: StockProjection.totalQuantity = 5 (still below threshold)
WHEN:  User POSTs /api/v1/alerts/{id}/dismiss
THEN:  Alert.isRead = true, readAt is set
  AND: Alert.isResolved remains false
  AND: Alert remains visible when querying isResolved=false
  AND: Another MovementRecorded event does NOT create a duplicate alert (original still active)
```

### 9.2 NEAR_EXPIRY Alerts

#### Scenario 6: Batch enters warning window → NEAR_EXPIRY alert created

```
GIVEN: NEAR_EXPIRY rule exists with daysBeforeExpiry = 90
  AND: Batch B1 has expirationDate = 85 days from today (within warning window)
  AND: No alert was created TODAY for this batch
WHEN:  The daily near-expiry check job runs (06:00)
THEN:  A NEAR_EXPIRY alert is created (severity = WARNING)
  AND: alert.currentValue = 85, alert.thresholdValue = 90
  AND: alert.batchId = B1
  AND: NearExpiryAlertTriggered event is published
```

#### Scenario 7: Same batch, next day → second alert created (daily reminder)

```
GIVEN: Batch B1 has NEAR_EXPIRY alert created yesterday (daysUntilExpiry = 85)
  AND: Today is the next day (daysUntilExpiry = 84)
  AND: Batch B1 is still ACTIVE / NEAR_EXPIRY
WHEN:  The daily near-expiry check job runs
THEN:  A NEW NEAR_EXPIRY alert is created for today
  AND: The previous alert remains unchanged (resolved or not — independent)
```

#### Scenario 8: Batch within 30 days → severity changes to CRITICAL

```
GIVEN: Batch B1 has expirationDate = 25 days from today
  AND: NEAR_EXPIRY rule exists with daysBeforeExpiry = 90
WHEN:  The daily near-expiry check job runs
THEN:  A NEAR_EXPIRY alert is created (severity = CRITICAL)
  AND: alert.currentValue = 25, alert.thresholdValue = 90
  AND: CRITICAL because daysUntilExpiry <= 30
```

#### Scenario 9: Batch already expired → NEAR_EXPIRY alert does NOT fire

```
GIVEN: Batch B1 has status = EXPIRED, expirationDate = yesterday
WHEN:  The daily near-expiry check job runs
THEN:  No NEAR_EXPIRY alert is created for B1
  AND: B1 is excluded from the eligible batch query (EXPIRED batches excluded)
```

### 9.3 EXPIRED Alerts

#### Scenario 10: Batch expires → EXPIRED alert created (CRITICAL)

```
GIVEN: EXPIRED rule exists (always active by default)
  AND: Batch B1 status transitions to EXPIRED
WHEN:  BatchExpired event is consumed
  AND: No active EXPIRED alert exists for B1
THEN:  An EXPIRED alert is created (severity = CRITICAL)
  AND: alert.batchId = B1
  AND: alert.productName is a snapshot of the product name
  AND: ExpiredAlertTriggered event is published
```

#### Scenario 11: Same batch, next day → NO duplicate (one-time alert)

```
GIVEN: EXPIRED alert already exists for Batch B1 (isResolved = false)
WHEN:  The daily expired check job runs (or BatchExpired fires again)
THEN:  No duplicate EXPIRED alert is created
  AND: The existing alert remains (no change)
```

#### Scenario 12: Expired batch written off → EXPIRED alert resolved by system

```
GIVEN: EXPIRED alert exists for Batch B1 (isResolved = false)
WHEN:  BatchWrittenOff event is consumed (Batch B1 → WRITTEN_OFF)
THEN:  The EXPIRED alert is resolved (isResolved = true, resolvedBy = "SYSTEM")
  AND: resolvedAt is set
```

### 9.4 BATCH_QUARANTINED Alerts

#### Scenario 13: Batch quarantined → BATCH_QUARANTINED alert created (CRITICAL)

```
GIVEN: BATCH_QUARANTINED rule exists (always active by default)
  AND: Batch B1 is quarantined (status → QUARANTINED)
WHEN:  BatchQuarantined event is consumed
  AND: No active BATCH_QUARANTINED alert exists for B1
THEN:  A BATCH_QUARANTINED alert is created (severity = CRITICAL)
  AND: alert.batchId = B1
```

#### Scenario 14: Already quarantined → no duplicate

```
GIVEN: BATCH_QUARANTINED alert already exists for Batch B1
WHEN:  Another BatchQuarantined event is consumed for B1 (e.g., re-processing)
THEN:  No duplicate alert is created
  AND: The existing alert remains unchanged
```

### 9.5 Alert Lifecycle

#### Scenario 15: User dismisses WARNING alert → isRead=true, isResolved=false

```
GIVEN: LOW_STOCK alert exists (severity = WARNING, isRead = false)
WHEN:  User POSTs /api/v1/alerts/{id}/dismiss
THEN:  Response is 200 OK
  AND: Alert.isRead = true, alert.readAt is set
  AND: Alert.isResolved = false
```

#### Scenario 16: User resolves CRITICAL alert → isResolved=true

```
GIVEN: EXPIRED alert exists (severity = CRITICAL, isResolved = false)
WHEN:  User with manager role POSTs /api/v1/alerts/{id}/resolve
       with { "resolution": "Batch B1 disposed per SOP-42", "resolvedBy": "user-123" }
THEN:  Response is 200 OK
  AND: Alert.isResolved = true, alert.resolvedAt is set, alert.resolvedBy = "user-123"
```

#### Scenario 17: Dismiss CRITICAL alert → rejected (must resolve)

```
GIVEN: EXPIRED alert exists (severity = CRITICAL, isResolved = false)
WHEN:  User POSTs /api/v1/alerts/{id}/dismiss
THEN:  Response is 409 Conflict
  AND: Error code is CRITICAL_ALERT_CANNOT_DISMISS
  AND: Message explains critical alerts must be resolved, not dismissed
```

### 9.6 Dashboard Integration

#### Scenario 18: Alert summary shows correct counts by type and severity

```
GIVEN: 3 unresolved LOW_STOCK alerts (2 WARNING, 1 CRITICAL)
  AND: 2 unresolved NEAR_EXPIRY alerts (2 WARNING)
  AND: 1 unresolved EXPIRED alert (1 CRITICAL)
WHEN:  User GETs /api/v1/alerts/summary
THEN:  Response is 200 OK
  AND: totalActive = 6
  AND: critical = 2, warning = 4, info = 0
  AND: byType.LOW_STOCK = 3, byType.NEAR_EXPIRY = 2, byType.EXPIRED = 1, byType.BATCH_QUARANTINED = 0
```

#### Scenario 19: Product deactivated → active alerts for that product are resolved

```
GIVEN: Product P1 has an active LOW_STOCK alert (isResolved = false)
  AND: Product P1 has an active NEAR_EXPIRY alert (isResolved = false) for batch B1 belonging to P1
WHEN:  ProductDeactivated event is consumed for P1
THEN:  The LOW_STOCK alert is resolved (isResolved = true, resolvedBy = "SYSTEM")
  AND: The NEAR_EXPIRY alert is resolved (isResolved = true, resolvedBy = "SYSTEM")
  AND: No new alerts are created for P1 while it remains deactivated
```

---

## 10. Frontend Specifications

### 10.1 Component Tree

```
components/alerts/
├── AlertPanel.tsx                ← Main alerts page: filterable list
├── AlertCard.tsx                 ← Individual alert display
├── AlertBadge.tsx                ← Small count badge for sidebar
├── AlertSummaryRow.tsx           ← Dashboard widget
└── AlertPanelSkeleton.tsx        ← Loading skeleton
```

### 10.2 AlertPanel

**Type:** Data page with filters.

**Props:**
```typescript
interface AlertPanelProps {
  // No props — fetches data internally via use-alerts hook
}
```

**Renders:**
- Tab bar: "Active alerts" (default), "Resolved"
- Filter bar: type dropdown, severity dropdown, product search
- List of AlertCard components (paginated, 20 per page)
- "Load more" button or pagination controls

**Data fetching:**
- Active tab: `GET /api/v1/alerts?isResolved=false&page=0&size=20`
- Resolved tab: `GET /api/v1/alerts?isResolved=true&page=0&size=20`
- Filters passed as query params to the same endpoint

**States:**
- **Loading:** Skeleton list (6-8 AlertCard placeholders)
- **Empty (active):** "No alerts — all clear" illustration with green checkmark
- **Empty (resolved):** "No resolved alerts" illustration
- **Error:** Error message + retry button
- **Data:** Cards in paginated list

### 10.3 AlertCard

**Type:** Presentational component.

**Props:**
```typescript
interface AlertCardProps {
  alert: AlertSummary;
  onDismiss: (id: string) => Promise<void>;
  onResolve: (id: string) => Promise<void>;
  currentUserRole: 'assistant' | 'manager';
}
```

**Renders:**
- Severity color bar (left border): red = CRITICAL, yellow = WARNING, blue = INFO
- Icon by alert type (stock icon, clock icon, warning icon, shield icon)
- Product name (bold), message (secondary text), timestamp (relative, e.g. "2h ago")
- Severity badge and type badge
- Action buttons:
  - Dismiss button (WARNING and INFO only) — "Mark as read"
  - Resolve button (manager role only) — "Resolve"
  - Dismiss disabled for CRITICAL (tooltip: "Must resolve, not dismiss")
- Click on card navigates to related product detail page

**States:**
- **Default:** Full card with all actions
- **Dismissing:** Button spinner, card stays visible
- **Resolving:** Button spinner, card transitions out on success
- **CRITICAL:** Dismiss button hidden, "Resolve" button prominent with red styling

### 10.4 AlertBadge

**Type:** Presentational badge for sidebar.

**Props:**
```typescript
interface AlertBadgeProps {
  count: number;          // total unresolved CRITICAL + WARNING
  criticalCount: number;
}
```

**Renders:**
- Colored circle with count number
- Color: red if criticalCount > 0, yellow if only WARNING, hidden if 0
- Small size (24px circle)

**States:**
- **Loading:** Skeleton circle (gray)
- **Zero:** Not rendered (hidden)
- **Warnings only:** Yellow badge with count
- **Has critical:** Red badge with count

### 10.5 AlertSummaryRow

**Type:** Dashboard widget.

**Props:**
```typescript
interface AlertSummaryRowProps {
  totalActive: number;
  critical: number;
  warning: number;
  info: number;
}
```

**Renders:**
- Horizontal row of stat cards:
  - "Active Alerts": totalActive (default color)
  - "Critical": critical (red, bold)
  - "Warnings": warning (yellow)
  - "Info": info (blue)
- Click on any stat navigates to `/alerts?severity=X&isResolved=false`

**States:**
- **Loading:** Skeleton stat cards (4 placeholders)
- **Zero:** Shows "0" for all categories with "All clear" subtitle
- **Data:** Color-coded stat cards

---

## 11. Testing Requirements

Strict TDD: Domain tests MUST be written BEFORE domain implementation.

### 11.1 Domain Unit Tests (JUnit 5)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `AlertRuleTest` | Create rule with valid conditions; Create with empty conditions → throws; Toggle enabled/disabled; Create duplicate name → throws; LOW_STOCK conditions validation (threshold > 0); System default rule creation |
| `AlertInstanceTest` | Create alert; Dismiss WARNING — success; Dismiss CRITICAL — throws CRITICAL_ALERT_CANNOT_DISMISS; Resolve alert; Resolve already resolved — idempotent; Alert lifecycle transitions (triggered → read → resolved); Severity mapping by type |

### 11.2 Application Tests (JUnit 5 + Mockito)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `AlertEvaluationServiceTest` | LOW_STOCK triggered on state transition; LOW_STOCK no duplicate; NEAR_EXPIRY daily deduplication; EXPIRED one-time alert; BATCH_QUARANTINED alert; Evaluates when rule is enabled vs. disabled |
| `AlertLifecycleServiceTest` | Dismiss WARNING — success; Dismiss CRITICAL — throws; Resolve CRITICAL — success; Auto-resolve on StockReplenished; Auto-resolve on BatchWrittenOff; Auto-resolve on ProductDeactivated |
| `AlertRuleServiceTest` | Create custom rule; Create duplicate name → throws; Update rule; Disable rule (set isEnabled = false); Default rule creation on ProductRegistered event; Update default rule on ProductUpdated event |
| `AlertSummaryServiceTest` | Returns correct counts by type and severity; Returns zero counts when no alerts |

### 11.3 Integration Tests (Testcontainers + SpringBootTest)

| Test Class | Scenarios Covered |
|-----------|------------------|
| `JpaAlertRuleRepositoryTest` | CRUD; Tenant isolation — tenant A cannot see tenant B rules; Unique constraint on (tenant_id, name); Find enabled rules by type |
| `JpaAlertInstanceRepositoryTest` | CRUD; Find active by product; Find active by batch; Deduplication query (active + type + batch); Pagination query; Tenant isolation |
| `AlertEventConsumerIntegrationTest` | Consume StockDepleted → alert created; Consume StockReplenished → alert resolved; Consume BatchExpired → alert created; Consume BatchQuarantined → alert created; Consume BatchWrittenOff → alert resolved; Consume ProductDeactivated → alerts resolved |
| `AlertScheduledJobIntegrationTest` | Daily NEAR_EXPIRY evaluation creates alerts; Idempotent — second run does NOT create duplicates; Expired batches excluded from NEAR_EXPIRY evaluation |
| `AlertRestControllerIntegrationTest` | Full REST endpoints with real DB — all endpoints tested; Validation error responses; 404 for non-existent entities; 409 for dismiss CRITICAL; Tenant isolation on all endpoints |

### 11.4 Architecture Tests (ArchUnit)

| Rule | Enforcement |
|------|-------------|
| Alerting domain does NOT depend on Inventory or Sales domain | ArchUnit — consumes events only via infrastructure layer |
| Alerting application depends ONLY on domain | ArchUnit |
| Alerting infrastructure does NOT depend on other context's infrastructure | ArchUnit |
| Event consumers are in `infrastructure/eventhandler` package | ArchUnit |
| Controllers are in `infrastructure/rest` package | ArchUnit |

### 11.5 Frontend Tests (Vitest + Testing Library)

| Test File | Scenarios Covered |
|-----------|------------------|
| `AlertCard.test.tsx` | Renders with correct severity color bar (CRITICAL=red, WARNING=yellow); Shows dismiss button for WARNING; Hides dismiss button for CRITICAL; Shows resolve button for manager role; Click navigates to product detail |
| `AlertPanel.test.tsx` | Renders alert list with data; Filters by type; Tab toggle (Active/Resolved); Loading skeleton; Empty state with "All clear" illustration; Error state with retry; Pagination |
| `AlertBadge.test.tsx` | Shows count; Red when critical > 0; Yellow when only warnings; Hidden when count = 0; Loading state |
| `AlertSummaryRow.test.tsx` | Renders all 4 stat cards; Color-coded numbers; Click navigates to filtered alerts page; Zero state shows "All clear" |
| `DismissButton.test.tsx` | Enabled for WARNING; Disabled and shows tooltip for CRITICAL; Loading state during dismiss |

### 11.6 E2E Test (Playwright)

```
1. GIVEN: Product "Ibuprofeno 400mg" exists (via Product Catalog API)
2. WHEN:  User receives stock for that product:
          Batch A: qty=5, exp=2026-12-15 (below LOW_STOCK threshold of 10)
3. AND:   User views AlertPanel — LOW_STOCK alert appears (severity: WARNING)
4. AND:   User dismisses the alert
5. AND:   User receives more stock: qty=100 (above threshold)
6. AND:   User views AlertPanel — LOW_STOCK alert shows as resolved
7. AND:   User waits for batch to expire (or test data manipulation)
8. THEN:  EXPIRED alert appears (severity: CRITICAL)
9. AND:   User attempts to dismiss EXPIRED alert → rejected (must resolve)
10. AND:  User resolves EXPIRED alert (manager role)
11. AND:  AlertPanel shows alert as resolved
```

---

## 12. Database Schema (Flyway V4)

```sql
-- V4__create_schema_alerting.sql

CREATE SCHEMA IF NOT EXISTS alerting;

CREATE TABLE alerting.alert_rules (
    alert_rule_id UUID PRIMARY KEY,
    tenant_id     VARCHAR(36) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    alert_type    VARCHAR(30) NOT NULL,
    conditions    JSONB NOT NULL,
    is_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    is_default    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_alert_rules_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_alert_rules_type
    ON alerting.alert_rules (tenant_id, alert_type, is_enabled);

CREATE TABLE alerting.alert_instances (
    alert_instance_id UUID PRIMARY KEY,
    tenant_id         VARCHAR(36) NOT NULL,
    alert_rule_id     UUID REFERENCES alerting.alert_rules(alert_rule_id),
    alert_type        VARCHAR(30) NOT NULL,
    severity          VARCHAR(10) NOT NULL,
    product_id        UUID,
    product_name      VARCHAR(200),
    batch_id          UUID,
    lot_number        VARCHAR(100),
    message           TEXT NOT NULL,
    current_value     INTEGER,
    threshold_value   INTEGER,
    is_read           BOOLEAN NOT NULL DEFAULT FALSE,
    is_resolved       BOOLEAN NOT NULL DEFAULT FALSE,
    triggered_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at           TIMESTAMP WITH TIME ZONE,
    resolved_at       TIMESTAMP WITH TIME ZONE,
    resolved_by       VARCHAR(100)
);

CREATE INDEX idx_alerts_active
    ON alerting.alert_instances (tenant_id, is_resolved, severity, triggered_at DESC)
    WHERE is_resolved = FALSE;

CREATE INDEX idx_alerts_product
    ON alerting.alert_instances (tenant_id, product_id, is_resolved);

CREATE INDEX idx_alerts_batch
    ON alerting.alert_instances (tenant_id, batch_id, alert_type);

CREATE INDEX idx_alerts_type_severity
    ON alerting.alert_instances (tenant_id, alert_type, severity, triggered_at DESC);
```

---

## 13. Error Handling

### 13.1 Standard Error Response Format

```json
{
  "status": 409,
  "code": "CRITICAL_ALERT_CANNOT_DISMISS",
  "message": "Critical alerts cannot be dismissed. They must be resolved by addressing the underlying condition.",
  "correlationId": "req-abc-123",
  "timestamp": "2026-06-18T15:00:00Z"
}
```

### 13.2 Exception Mapping

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| `AlertNotFoundException` | 404 | ALERT_NOT_FOUND |
| `AlertRuleNotFoundException` | 404 | ALERT_RULE_NOT_FOUND |
| `InvalidAlertRuleException` | 400 | INVALID_ALERT_RULE |
| `CriticalAlertDismissException` | 409 | CRITICAL_ALERT_CANNOT_DISMISS |
| `AlreadyResolvedException` | 409 | ALREADY_RESOLVED |
| `DuplicateAlertRuleException` | 409 | DUPLICATE_ALERT_RULE |
| `TenantMismatchException` | 403 | TENANT_MISMATCH |
| `ValidationErrorException` | 400 | VALIDATION_ERROR |

---

## 14. Design Decisions Log

| ID | Decision | Rationale | Date |
|----|----------|-----------|------|
| ALR-DEC-01 | LOW_STOCK fires on state TRANSITION, not continuously | Prevents alert storms on high-frequency movements. Alert fires ONCE when entering LOW_STOCK, not every time stock decreases further | 2026-06-18 |
| ALR-DEC-02 | NEAR_EXPIRY is scheduled DAILY, not event-driven | Expiration is a known future date — no need to re-evaluate on every movement. Daily cron avoids unnecessary computation | 2026-06-18 |
| ALR-DEC-03 | EXPIRED alerts fire ONCE per batch | Once a batch expires, it stays expired. No daily re-alerting needed. Resolved when batch is written off | 2026-06-18 |
| ALR-DEC-04 | CRITICAL alerts cannot be dismissed — must be resolved | Regulatory: expired stock and quarantine are not "dismissable" — action must be taken. Dismiss would hide safety-critical information | 2026-06-18 |
| ALR-DEC-05 | No email/push notifications for MVP | In-app alerts only. Notification delivery infrastructure adds complexity not needed for initial launch | 2026-06-18 |
| ALR-DEC-06 | Default LOW_STOCK rules created on ProductRegistered | Reduces manual configuration. Default threshold = product.defaultReorderPoint | 2026-06-18 |
| ALR-DEC-07 | Alert severity is denormalized in AlertInstance | Enables efficient querying (no join to rule + recomputation on every read). Severity is snapshot at time of creation | 2026-06-18 |
| ALR-DEC-08 | No polling — event-driven + scheduled only | Alerting reacts to events (StockDepleted, BatchExpired) and scheduled checks (near-expiry). No periodic database scans for MVP | 2026-06-18 |
| ALR-DEC-09 | AlertRule.conditions stored as JSONB | Type-specific conditions (LOW_STOCK has thresholdQuantity, NEAR_EXPIRY has daysBeforeExpiry). JSONB enables schema flexibility without separate tables per type | 2026-06-18 |
| ALR-DEC-10 | `productName` and `lotNumber` are snapshots in AlertInstance | Alert must be reproducible as it appeared when triggered. If product is later renamed, the alert still shows the original name | 2026-06-18 |
| ALR-DEC-11 | `alertRuleId` is nullable in AlertInstance | System-generated alerts (EXPIRED, BATCH_QUARANTINED) may not correspond to a user-defined rule. Nullable FK avoids mandatory rule dependency | 2026-06-18 |

---

## 15. Inventory Design Validation (Cross-Check)

The Alerting spec validates these Inventory design points:

| Check | Result | Notes |
|-------|--------|-------|
| 1. **Batch lifecycle events** — BatchExpired, BatchNearExpiry, BatchDepleted consumed correctly | ✅ PASSES | Event payloads carry sufficient data (batchId, productId, quantity, expirationDate) |
| 2. **StockProjection** — LOW_STOCK evaluation reads StockProjection | ✅ PASSES | StockProjection is the canonical read model for current stock position. Acceptable eventual consistency (seconds delay) |
| 3. **MovementRecorded** — triggers low-stock re-evaluation | ✅ PASSES | MovementRecorded event carries productId and direction/quantity. Alerting re-evaluates active LOW_STOCK rules |
| 4. **Batch status transitions** — EXPIRED alerts only fire on ACTIVE/NEAR_EXPIRY→EXPIRED | ✅ PASSES | Already-DEPLETED or already-QUARANTINED batches do not fire EXPIRED alerts. BatchExpired event only fires on valid status transitions |
| 5. **Eventual consistency** — acceptable for alerting | ✅ PASSES | A few seconds delay between stock movement and alert creation is clinically acceptable. The system is not a real-time monitoring device |
| 6. **StockReplenished event** — consumed to auto-resolve LOW_STOCK | ✅ PASSES | Event carries productId and newQuantity. Alerting resolves active LOW_STOCK alert for that product |
| 7. **BatchWrittenOff** — consumed to resolve EXPIRED and QUARANTINED alerts | ✅ PASSES | Inventory's write-off flow (INV scenarios 17-19) pairs with Alerting's resolution (ALR scenario 12) |

---

## Appendix A: Package Structure (Hexagonal)

```
alerting/
├── domain/
│   ├── AlertRule.java
│   ├── AlertInstance.java
│   ├── AlertType.java
│   ├── AlertSeverity.java
│   ├── AlertRuleRepository.java (port)
│   ├── AlertInstanceRepository.java (port)
│   ├── events/
│   │   ├── LowStockAlertTriggeredEvent.java
│   │   ├── NearExpiryAlertTriggeredEvent.java
│   │   └── ExpiredAlertTriggeredEvent.java
│   └── exceptions/
│       ├── CriticalAlertDismissException.java
│       ├── AlertNotFoundException.java
│       └── AlertRuleNotFoundException.java
│
├── application/
│   ├── AlertEvaluationService.java       (evaluates rules against events)
│   ├── AlertLifecycleService.java        (dismiss, resolve, auto-resolve)
│   ├── AlertRuleService.java             (CRUD for rules)
│   ├── AlertSummaryService.java          (summary counts)
│   └── DefaultAlertRuleInitializer.java  (creates default rules on ProductRegistered)
│
└── infrastructure/
    ├── persistence/
    │   ├── JpaAlertRuleRepository.java
    │   ├── JpaAlertInstanceRepository.java
    │   ├── AlertRuleEntity.java
    │   └── AlertInstanceEntity.java
    ├── rest/
    │   ├── AlertController.java
    │   ├── AlertRuleController.java
    │   └── dto/
    │       ├── AlertResponse.java
    │       ├── AlertSummaryResponse.java
    │       ├── CreateAlertRuleRequest.java
    │       ├── DismissAlertResponse.java
    │       └── ResolveAlertRequest.java
    ├── eventhandler/
    │   ├── InventoryEventConsumer.java     (StockDepleted, StockReplenished, BatchExpired, etc.)
    │   ├── ProductCatalogEventConsumer.java (ProductRegistered, ProductDeactivated)
    │   └── AlertEventPublisher.java        (publishes LowStockAlertTriggered etc.)
    └── scheduled/
        ├── NearExpiryAlertJob.java
        └── ExpiredBatchCheckJob.java
```
