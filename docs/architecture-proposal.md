# Proposal: Architecture Blueprint — MediStock MVP

## Intent

Define the foundational architecture, bounded contexts, delivery roadmap, and MVP scope for MediStock — a greenfield pharmaceutical inventory management SaaS. This proposal establishes the Hexagonal + DDD + modular monolith blueprint that will govern all subsequent implementation phases, ensuring domain purity, testability, and future extractability.

---

## 1. Product Vision

MediStock is a **pharmaceutical inventory management SaaS** purpose-built for independent pharmacies and pharmacy chains. It replaces the spreadsheets, paper logs, and fragmented systems that pharmacies use today with a unified platform that enforces **FEFO (First Expiry, First Out)** at every transaction, maintains a complete regulatory audit trail, and surfaces actionable alerts before products expire. What makes MediStock different is its domain-first design: FEFO is not a suggestion — it is enforced by the core algorithm. Every stock movement is immutable and traceable. Multi-tenant isolation is baked into the schema foundation. The UI follows premium SaaS aesthetics (Linear, Stripe, Vercel-inspired card grids) — no hospital imagery, no Bootstrap, no generic admin panels.

---

## 2. MVP Scope — Explicit Boundaries

### In Scope (MVP)

| Area | Deliverable |
|------|-------------|
| **Product Catalog** | Product CRUD, categories, SKU management |
| **Batch/Lot Receiving** | Receive stock with expiration dates, batch registration |
| **FEFO Picking Engine** | Core algorithm with optimistic locking on Batch |
| **Stock Positions** | Simplified: physical stock + available stock only |
| **Sales Recording** | Basic sales with FEFO allocation |
| **Movement Audit Trail** | Append-only movements table |
| **Alerting** | Near-expiry (daily eval) + low-stock (on transition) |
| **Dashboard** | Stock summary cards, active alert indicators |
| **Product UI** | Card-based inventory grid per design reference |
| **Multi-Tenant** | Tenant isolation via tenant_id column on every table |

### Out of Scope (Post-MVP)

| Item | Rationale |
|------|-----------|
| Returns management | Quarantine handling deferred — all returns enter quarantine by default |
| Advanced stock states (quarantine, reserved, in-transit) | Simplification for MVP — physical + available only |
| POS integration | No external system integration in MVP |
| Prescription number tracking | Pharmacy-specific feature, not in core inventory scope |
| Cold chain tracking | Environmental monitoring deferred |
| Serial number tracking (controlled substances) | Regulatory feature for future phase |
| Reporting module | Basic dashboard only — no exportable reports |
| WebSocket real-time updates | Polling-based alerts sufficient for MVP |
| BFF layer | Direct Next.js → Spring Boot calls for MVP |
| Partitioning / archival | Append-only table without partitioning |
| Pessimistic locking | Optimistic locking on Batch only |

---

## 3. Development Roadmap — Phased Delivery (6 Phases)

All phases build on the hexagonal package structure established in Phase 1.

```
Phase 1: Foundation ──────────────────────────────────────┐
                                                           │
Phase 2: Product Catalog ──────────┐                       │
                                   │                       │
Phase 3: Inventory (Core) ─────────┤── Parallel start ─────┤
                                   │                       │
Phase 4: Sales ────────────────────┤                       │
                                   │                       │
Phase 5: Alerting ─────────────────┤                       │
                                   │                       │
Phase 6: Dashboard & Polish ───────┘                       │
                                                           │
              All phases depend on Phase 1 (Foundation) ───┘
```

### Phase 1: Foundation (Scaffolding)

**Dependencies:** None (starting point)

| Task | Detail |
|------|--------|
| Gradle project setup | Spring Boot 3.x, Java 21, dependency management |
| Hexagonal package structure | `shared/` kernel + 5 bounded context stubs (productcatalog, inventory, sales, alerting, reporting) |
| Flyway migrations | Schema-per-context migrations (V1–V4) |
| PostgreSQL dev setup | Docker Compose or Supabase connection |
| Next.js project | App Router, Tailwind CSS 4, shadcn/ui, TanStack Query |
| GitHub Actions CI | Build + test for both frontend and backend |
| ArchUnit test | Enforce hexagonal package dependency rules |
| **Est. lines:** 1,200–1,800 | |

### Phase 2: Product Catalog Context

**Dependencies:** Phase 1

| Task | Detail |
|------|--------|
| Product aggregate | Domain entity + JPA persistence with version for optimistic locking |
| Category value object | Embedded in Product |
| REST API | CRUD: POST/PUT/GET/DELETE /api/products, GET /api/products/{id} |
| Frontend product list | Card grid with ProductCard component |
| Product detail page | Full product view + edit form |
| Tests | Domain unit (JUnit 5), application (Mockito), integration (Testcontainers), Vitest components |
| **Est. lines:** 1,500–2,200 | |

### Phase 3: Inventory Context (Core)

**Dependencies:** Phase 1, Phase 2 (products must exist to receive stock)

| Task | Detail |
|------|--------|
| Batch aggregate | Domain entity with `version` for optimistic locking, `expirationDate`, `status` |
| FEFO picking service | Domain service: sorts eligible batches by expiration, allocates quantities, applies locks |
| Movement aggregate | Immutable, append-only record of every stock change |
| Stock projection | Event-updated read model (updated by domain event handler) |
| REST API | POST /api/inventory/receive, GET /api/inventory/batches, GET /api/inventory/stock-summary |
| Frontend inventory views | Batch list, stock receiving form, stock summary |
| Tests | FEFO algorithm edge cases (10+), optimistic locking conflict tests, Testcontainers for repositories |
| **Est. lines:** 2,500–3,500 | |

### Phase 4: Sales Context

**Dependencies:** Phase 3 (FEFO engine must be operational)

| Task | Detail |
|------|--------|
| Sale aggregate | Sale + SaleItem + BatchAllocation value objects |
| FEFO allocation at sale | Domain service calls FefoPickService, creates Sales + Movements atomically |
| REST API | POST /api/sales, GET /api/sales/{id}, POST /api/sales/{id}/void |
| Movement creation | Sale → Movement via domain event (SaleCompletedEvent) |
| Stock projection update | Event handler updates stock read model |
| Frontend sales view | Sale entry form with FEFO allocation display, sales list |
| Tests | Allocation scenarios, void with FEFO re-allocation |
| **Est. lines:** 1,500–2,200 | |

### Phase 5: Alerting Context

**Dependencies:** Phase 3 (depends on batch/movement data)

| Task | Detail |
|------|--------|
| AlertRule aggregate | Configurable rules (thresholds, type, enabled) |
| AlertInstance aggregate | Actual alerts generated (resolved/dismissed lifecycle) |
| Scheduled evaluation | Near-expiry: daily cron. Low-stock: on movement events |
| REST API | GET /api/alerts, POST /api/alerts/{id}/dismiss, POST /api/alerts/{id}/resolve |
| Frontend alerts panel | Alert cards with severity, product info, dismiss/resolve actions |
| Dashboard alert indicators | Count badges in sidebar + dashboard header |
| Tests | Rule evaluation logic, duplicate suppression |
| **Est. lines:** 1,000–1,500 | |

### Phase 6: Dashboard & Polish

**Dependencies:** Phases 2–5

| Task | Detail |
|------|--------|
| Dashboard page | Stock summary cards (total products, low stock, near expiry, recent movements) |
| Search/filter on product grid | Product search bar with category filter |
| Status badges | Visual indicators (in stock, low stock, out of stock, expiring soon) |
| Error boundaries | React error boundaries + API error handling |
| Loading states | Skeleton loaders for all data-dependent views |
| Empty states | Design for zero-data initial experience |
| **Est. lines:** 800–1,200 | |

---

## 4. Architecture Blueprint

### Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Architecture style** | Hexagonal (Ports & Adapters) | Domain purity, testability via port mocking, future extraction to microservices |
| **Deployment model** | Modular monolith | Single deployable JAR, ArchUnit-enforced package boundaries, low operational complexity |
| **DB isolation** | Schema-per-context | Each bounded context owns its schema (`product_catalog`, `inventory`, `sales`, `alerting`). Enables future extraction to separate DBs |
| **Stock model** | Projection (event-updated read model) | Avoids dual-write problem. StockSummary table rebuilt from batches on startup |
| **FEFO locking** | Optimistic (version column on Batch) | Adequate for pharmacy volumes (hundreds of picks/day, not thousands/second). No deadlock risk |
| **CQRS** | Lite (stock projection table only) | Single read model for stock queries. Full CQRS is over-engineering for MVP |
| **Eventing** | Spring `ApplicationEventPublisher` | Transactional, in-process, zero infrastructure. Domain events → same transaction |
| **API style** | REST JSON | Universal, well-understood, sufficient for MVP. GraphQL/gRPC considered and deferred |
| **BFF** | None (MVP) | Direct Next.js → Spring Boot calls. BFF layer added when mobile or partner API needs emerge |
| **Auth** | JWT (stateless) | Tenant context embedded in JWT claims. Auth provider abstracted behind port for future swap |
| **Frontend state** | TanStack Query (server state) + URL state | No global client store for MVP. Server cache IS the source of truth |

### Hexagonal Package Structure (Per Context)

```
context-name/
├── domain/           # Entities, value objects, repository interfaces, domain services
│   ├── *.java
│   └── events/
├── application/      # Use cases (inbound ports), orchestration, no framework deps
│   └── *.java
└── infrastructure/   # Adapters for ports (JPA, REST controllers, event handlers)
    ├── persistence/
    ├── rest/
    │   └── dto/
    └── eventhandler/
```

### Package Dependency Rules (ArchUnit-enforced)

```
domain: ZERO dependencies (pure Java)
application: depends ONLY on domain
infrastructure: depends on domain + framework (Spring, JPA)
                    ↓
              NEVER depends on another context's infrastructure
```

Cross-context communication: **domain events only**. One context emits a domain event; another context's infrastructure layer subscribes via `@TransactionalEventListener`.

---

## 5. Database Schema Strategy

### Schema-per-Context

| Schema | Tables | Managed By |
|--------|--------|------------|
| `product_catalog` | `products`, `categories` | Product Catalog context |
| `inventory` | `batches`, `movements`, `stock_summary` | Inventory context |
| `sales` | `sales`, `sale_items`, `batch_allocations` | Sales context |
| `alerting` | `alert_rules`, `alert_instances` | Alerting context |

### Flyway Migration Plan

| Migration | Schema | Content |
|-----------|--------|---------|
| `V1__create_schema_product_catalog.sql` | `product_catalog` | Products + categories tables |
| `V2__create_schema_inventory.sql` | `inventory` | Batches, movements, stock_summary |
| `V3__create_schema_sales.sql` | `sales` | Sales, sale_items, batch_allocations |
| `V4__create_schema_alerting.sql` | `alerting` | Alert rules + instances |

### Multi-Tenant Foundation

Every table includes `tenant_id VARCHAR(36) NOT NULL` as the first column. All queries filter by `tenant_id`. Index on `(tenant_id, ...)` for all primary access patterns. No row-level security — tenant isolation is application-enforced.

---

## 6. Frontend Architecture

### Module Structure

```
frontend/src/
├── app/                   # Next.js App Router pages
│   ├── (dashboard)/       # Layout group: sidebar + topbar
│   │   ├── page.tsx       # Dashboard overview
│   │   ├── products/
│   │   ├── inventory/
│   │   ├── sales/
│   │   ├── alerts/
│   │   └── settings/
│   ├── layout.tsx         # Root layout (AppShell)
│   └── providers.tsx      # QueryClient + Theme + Auth providers
│
├── components/
│   ├── ui/                # shadcn/ui primitives
│   ├── layout/            # Sidebar, Topbar, AppShell
│   ├── products/          # ProductCard, ProductGrid, ProductSearch
│   ├── inventory/         # BatchTable, StockBadge
│   ├── sales/             # SaleForm, FefoAllocation
│   ├── alerts/            # AlertCard
│   └── shared/            # StatusBadge, SearchInput, DataTable
│
├── lib/                   # api-client (TanStack Query wrapper), utils, constants
├── hooks/                 # use-products, use-batches, use-sales, use-alerts, use-stock
├── types/                 # TypeScript interfaces per domain
└── styles/globals.css
```

### Data Flow

```
Pages → Hooks (TanStack Query) → api-client (fetch wrapper) → Spring Boot REST API
```

No client-side state management library. TanStack Query handles server state caching, refetching, and optimistic updates. URL search params handle filter/sort state for shareable URLs.

---

## 7. FEFO Algorithm — Core Design

### Domain Service: `FefoPickService`

```
Input:  ProductId, requestedQuantity
Output: List<BatchAllocation> (batchId, quantity, expirationDate)

Algorithm:
1. Query eligible batches for product (status=AVAILABLE, expirationDate > now, quantity > 0)
2. Sort by expirationDate ASC (earliest expiry first)
3. Iterate, allocate requestedQuantity across batches
4. If sum(available) < requestedQuantity → throw InsufficientStockException
5. For each batch: apply optimistic lock check (version column)
6. On version conflict → retry up to 3 times, then fail
```

### Edge Cases (Covered in Tests)

- Batch expires between query and update → exclude via `expirationDate > now` in query
- Multiple concurrent picks for same batch → optimistic lock retry
- Partial batch allocation → remaining quantity stays available
- Zero-quantity batches → excluded by query
- Batch status transitions → only AVAILABLE batches are eligible

---

## 8. Testing Strategy (TDD-Compliant)

| Layer | Tool | Responsibility |
|-------|------|---------------|
| Domain unit tests | JUnit 5 + Mockito | FEFO algorithm, aggregate invariants, business rules (no Spring context) |
| Application tests | JUnit 5 + Mockito | Use case orchestration with mocked ports |
| Integration tests | Testcontainers + SpringBootTest | JPA repositories, Flyway migrations, full REST endpoints |
| Architecture tests | ArchUnit | Package dependency rules (hexagonal compliance) |
| Frontend unit | Vitest + Testing Library | Component rendering, hooks logic, utility functions |
| E2E | Playwright | Critical path: create product → receive stock → FEFO sale → verify stock |

**Strict TDD rule:** Domain tests MUST be written BEFORE domain implementation code. This is enforced by convention and review.

---

## 9. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| FEFO oversell on concurrent pick | Medium | High | Optimistic locking + retry (3 attempts) + integration test coverage |
| Stock projection out of sync | Low | Medium | Rebuild from Batch table on startup. Scheduled reconciliation job |
| Schema-per-context query complexity | Low | Low | DB views for cross-context reads (e.g., product + stock in one query) |
| Frontend API client boilerplate | Medium | Low | Typed hooks + generated TypeScript types from API responses |
| Premature optimization | Medium | Medium | MVP uses simplest approach (polling, optimistic locking). Profile before optimizing |
| Team unfamiliarity with hexagonal architecture | Medium | Medium | ArchUnit enforces structure. Domain tests validate isolation. Documentation and review |

---

## 10. Rollback Plan

| Phase | Rollback Strategy |
|-------|-------------------|
| Phase 1 (Foundation) | `git revert` scaffold. Destroy Docker Compose volumes. Reset DB |
| Phase 2 (Product Catalog) | Revert Flyway migration V1. Remove ProductController/UI components |
| Phase 3 (Inventory) | Revert V2 migration. Remove inventory endpoints and UI |
| Phase 4 (Sales) | Revert V3 migration. Remove sales endpoints |
| Phase 5 (Alerting) | Revert V4 migration. Remove alerting scheduled tasks |
| Phase 6 (Dashboard) | Revert page components and UI changes only |

General principle: each phase is a self-contained PR. Reverting one phase does not block others (with the exception of Phase 3 → Phase 4 dependency).

---

## 11. Delivery Strategy

| Dimension | Decision |
|-----------|----------|
| **Chained PRs** | Recommended — 6 phases = 6 sequential PRs |
| **Review budget** | 400 lines per PR (default). Phase 3 may exceed → split into sub-PRs |
| **First two phases** | Phase 1 (Foundation) + Phase 2 (Product Catalog) can be started simultaneously after Phase 1 skeleton is merged |
| **Total estimate** | 8,000–12,000 lines across all phases |

---

## 12. Success Criteria

- [ ] All 6 phases delivered with passing tests (unit + integration + ArchUnit)
- [ ] FEFO algorithm passes 10+ edge case scenarios with concurrent pick tests
- [ ] All bounded contexts respect hexagonal boundaries (ArchUnit: zero violations)
- [ ] Frontend renders card-based product grid per UI design spec
- [ ] E2E critical path passes: create product → receive stock → create sale → verify stock update
- [ ] Multi-tenant isolation verified (tenant A cannot see tenant B data)
- [ ] Optimistic locking retry mechanism verified with concurrent test
- [ ] Stock projection stays in sync after 100+ movements (reconciliation test)
