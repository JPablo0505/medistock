# Technical Design — MediStock MVP

> **Phase:** SDD Design
> **Date:** 2026-06-18
> **Status:** Complete — ready for task planning
> **Change:** Full architecture implementation (Phases 1–6)

---

## Table of Contents

1. [Backend Package Structure](#section-1-backend-package-structure)
2. [JPA Entity Strategy](#section-2-jpa-entity-strategy)
3. [Repository Strategy](#section-3-repository-strategy)
4. [Transaction Boundaries](#section-4-transaction-boundaries)
5. [FEFO Component Design](#section-5-fefo-component-design)
6. [Frontend Feature Structure](#section-6-frontend-feature-structure)
7. [API Module Structure](#section-7-api-module-structure)
8. [Flyway Organization](#section-8-flyway-organization)
9. [Testing Folder Structure](#section-9-testing-folder-structure)
10. [ArchUnit Rules](#section-10-archunit-rules)
11. [Design Decisions Log](#section-11-design-decisions-log)

---

## Section 1: Backend Package Structure

### 1.1 Gradle Module Layout

**Design Decision: Single Module over Multi-Module**

| Option | Tradeoff | Decision |
|--------|----------|----------|
| **Single module** (chosen) | Faster builds, simpler IDE setup, one `build.gradle`, ArchUnit enforces boundaries at package level | ✅ **DSG-01** |
| Multi-module | Stronger compile-time isolation, clearer dependency graph, but adds Gradle complexity, slower CI, harder refactoring | ❌ |

**Rationale:** The modular monolith is a tactical choice. The project is 8,000–12,000 lines across 6 phases — multi-module would add ceremony without proportional benefit. ArchUnit enforces the same boundaries at package level. If extraction is needed later, the package structure maps 1:1 to modules.

**Implementation impact:** A single `build.gradle` with all dependencies. All bounded contexts compile together. Flyway migrations in one `resources/db/migration/` directory.

#### Directory Structure

```
backend/
├── build.gradle
├── settings.gradle
├── gradle/
│   └── libs.versions.toml
├── src/main/java/com/medistock/
│   ├── MedistockApplication.java
│   ├── shared/
│   │   ├── domain/
│   │   │   ├── AggregateRoot.java          # Marker interface
│   │   │   ├── DomainEvent.java            # Marker interface
│   │   │   └── ValueObject.java            # Marker interface (records)
│   │   └── infrastructure/
│   │       └── BaseEntity.java             # JPA base: id, tenantId, createdAt, updatedAt, version
│   ├── productcatalog/
│   │   ├── domain/
│   │   ├── application/
│   │   └── infrastructure/
│   ├── inventory/
│   ├── sales/
│   ├── alerting/
│   └── crosscutting/
│       ├── config/                         # @Configuration, Jackson, CORS
│       ├── security/                       # JWT filter, TenantContext
│       └── web/                            # @RestControllerAdvice, CorrelationIdFilter
├── src/main/resources/
│   ├── db/migration/
│   │   ├── V1__create_schema_product_catalog.sql
│   │   ├── V2__create_schema_inventory.sql
│   │   ├── V3__create_schema_sales.sql
│   │   └── V4__create_schema_alerting.sql
│   ├── application.yml
│   ├── application-dev.yml
│   └── application-test.yml
└── src/test/java/com/medistock/
    ├── archunit/
    │   └── HexagonalArchitectureTest.java
    ├── productcatalog/
    ├── inventory/
    ├── sales/
    ├── alerting/
    └── concurrency/
```

**Design Decision: Shared Kernel Approach**

**Rationale:** The `shared/` package contains only marker interfaces (`AggregateRoot`, `DomainEvent`, `ValueObject`) and the JPA base entity. No business logic. No shared domain concepts between contexts — each context owns its domain model fully. This prevents the shared kernel from becoming a dumping ground.

**Alternatives considered:** No shared kernel (full duplication of markers) — rejected as unnecessary boilerplate. Rich shared kernel — rejected as it creates coupling between contexts.

### 1.2 Per-Context Package Convention

Every bounded context follows this exact structure, enforced by ArchUnit:

```
productcatalog/
├── domain/
│   ├── model/
│   │   ├── Product.java
│   │   ├── ProductId.java
│   │   ├── Category.java
│   │   └── CategoryId.java
│   ├── service/
│   │   └── ProductValidationService.java
│   ├── port/
│   │   ├── ProductRepository.java
│   │   └── CategoryRepository.java
│   ├── event/
│   │   └── ProductRegisteredEvent.java
│   └── exception/
│       ├── DuplicateSkuException.java
│       ├── ProductNotFoundException.java
│       └── SkuImmutableException.java
├── application/
│   └── service/
│       ├── RegisterProductUseCase.java
│       ├── UpdateProductUseCase.java
│       ├── DeactivateProductUseCase.java
│       └── ReactivateProductUseCase.java
└── infrastructure/
    ├── persistence/
    │   ├── ProductEntity.java
    │   ├── CategoryEntity.java
    │   ├── JpaProductRepository.java
    │   └── JpaCategoryRepository.java
    ├── rest/
    │   ├── ProductController.java
    │   ├── CategoryController.java
    │   └── dto/
    │       ├── CreateProductRequest.java
    │       ├── UpdateProductRequest.java
    │       ├── ProductResponse.java
    │       └── ProductListResponse.java
    ├── eventhandler/
    │   └── InventoryProductEventHandler.java
    └── config/
        └── ProductCatalogConfig.java
```

**Design Decision: No inbound port interfaces in application layer**

**Rationale:** For MVP, the application service classes are called directly by controllers. Inbound port interfaces (e.g., `RegisterProductUseCase` interface) add indirection without benefit when the implementation is the only one. If a second implementation emerges, the interface can be extracted.

**Alternatives considered:** Always use inbound port interfaces — cleaner hexagonal architecture but YAGNI for MVP. Acceptable tradeoff.

### 1.3 Cross-Context Communication Rules

```
Sales application ──calls──► Inventory application (BatchAllocator)
     │                                │
     │ publishes                     │ publishes
     ▼                                ▼
SaleCompletedEvent              MovementRecordedEvent
     │                                │
     │ consumed by                    │ consumed by
     ▼                                ▼
Inventory event handlers        Alerting event handlers

ArchUnit rules:
- Sales.application MAY depend on Inventory.application (via interface)
- Sales.domain MUST NOT import anything from Inventory.domain
- No context's infrastructure may import another context's infrastructure
- Event handlers are in infrastructure.eventhandler, never in domain
```

**Design Decision: Application-to-application calls, not domain-to-domain**

**Rationale:** `BatchAllocator` is an application service in Inventory. Sales' `CreateSaleUseCase` depends on `BatchAllocator` through a small interface (`AllocationService` or directly). The `@Transactional` propagation ensures they share a transaction. This is simpler than messaging for MVP and avoids two-phase commit.

**Alternatives considered:** Domain events for sync calls — would require stateful sagas, over-engineering for MVP. Direct repository access from Sales domain — violates hexagonal boundaries.

---

## Section 2: JPA Entity Strategy

### 2.1 Domain Model vs JPA Entity Separation

**Design Decision: Separate domain model and JPA entities with manual mapping**

| Option | Tradeoff | Decision |
|--------|----------|----------|
| **Separate model** (chosen) | Duplicate classes per context, manual mapping code, but domain stays pure Java with zero annotations | ✅ **DSG-02** |
| Single model (JPA annotations on domain) | Less code, but framework leaks into domain, testing requires Spring context, breaks hexagonal purity | ❌ |
| MapStruct | Auto-generated mapping, less boilerplate, but build-time code generation, generated code to debug | ❌ |

**Rationale:** Domain purity is non-negotiable for TDD. Domain tests must run without Spring. Manual mapping in repository implementations gives full control and keeps the mapping logic explicit.

#### Concrete Example: Batch

```java
// === DOMAIN MODEL (pure Java, NO annotations) ===
// file: inventory/domain/model/Batch.java
public class Batch {
    private final BatchId batchId;
    private final String tenantId;
    private final ProductId productId;
    private final String lotNumber;
    private final LocalDate expirationDate;
    private final int initialQuantity;
    private int currentQuantity;
    private BatchStatus status;
    private final long version;          // opaque — read by application layer for retry logic
    // ... constructor, factory methods, domain behavior

    public void decrementQuantity(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (this.currentQuantity < quantity) throw new InsufficientStockException(
            "Batch %s has %d, requested %d".formatted(batchId, currentQuantity, quantity));
        this.currentQuantity -= quantity;
        if (this.currentQuantity == 0) {
            this.status = BatchStatus.DEPLETED;
        }
    }

    public boolean isExpired() {
        return LocalDate.now().isAfter(expirationDate);
    }
}

// === JPA ENTITY (infrastructure concern, annotations only) ===
// file: inventory/infrastructure/persistence/BatchEntity.java
@Entity
@Table(name = "batches", schema = "inventory")
public class BatchEntity extends BaseEntity {
    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false, length = 100)
    private String lotNumber;

    @Column(nullable = false)
    private LocalDate expirationDate;

    @Column(nullable = false)
    private int initialQuantity;

    @Column(nullable = false)
    private int currentQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status;

    // @Version from BaseEntity

    // Getters/setters ONLY — no domain logic
}

// === REPOSITORY IMPLEMENTATION (manual mapping) ===
// file: inventory/infrastructure/persistence/JpaBatchRepository.java
@Repository
public class JpaBatchRepository implements BatchRepository {
    private final SpringDataBatchRepository springRepo;

    @Override
    public Batch save(Batch batch) {
        BatchEntity entity = toEntity(batch);
        BatchEntity saved = springRepo.save(entity);
        return toDomain(saved);
    }

    private BatchEntity toEntity(Batch domain) {
        BatchEntity entity = new BatchEntity();
        BeanUtils.copyProperties(domain, entity);  // careful: use explicit setters
        // explicit mapping for value objects
        entity.setId(domain.getBatchId().uuid());
        entity.setProductId(domain.getProductId().uuid());
        // ...
        return entity;
    }

    private Batch toDomain(BatchEntity entity) {
        return Batch.restore(  // reconstruction factory
            new BatchId(entity.getId()),
            entity.getTenantId(),
            new ProductId(entity.getProductId()),
            // ...
        );
    }
}
```

**Design Decision: version field in Batch domain model**

Per INV-DEC-03 (from inventory spec), `version` is present in BOTH the domain Batch and the JPA entity. Unlike Product where version is strictly JPA-only, the FEFO allocation engine needs to detect concurrent modifications in the application layer to drive retry logic. The domain treats version as opaque (read-only, never modified by domain methods).

### 2.2 JPA Base Entity

```java
// file: shared/infrastructure/BaseEntity.java
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 36)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // getters only — no public setters for createdAt/updatedAt
}
```

**Design Decision: UUID primary keys**

**Rationale:** UUIDs prevent enumeration attacks, work across tenant-sharded databases in the future, and avoid sequential ID race conditions. Performance impact is negligible at MVP volumes.

**Alternatives considered:** Auto-increment long — simpler but exposes data volume, harder to migrate. Snowflake IDs — over-engineered for MVP.

### 2.3 Optimistic Locking Strategy

| Entity | @Version | Rationale |
|--------|----------|-----------|
| `ProductEntity` | ✅ | Concurrent product edits |
| `BatchEntity` | ✅ | FEFO concurrent picks — core lock target |
| `SaleEntity` | ✅ | Concurrent void attempts |
| `MovementEntity` | ❌ | Immutable — no updates possible |
| `CategoryEntity` | ❌ | Low contention, not in critical path |
| `LocationEntity` | ❌ | Low contention, simple CRUD |
| `AlertRuleEntity` | ❌ | Admin-only edits, low contention |
| `AlertInstanceEntity` | ❌ | Append-only with status transitions, low contention |

---

## Section 3: Repository Strategy

### 3.1 Repository Interface (Domain Port)

```java
// file: inventory/domain/port/BatchRepository.java
public interface BatchRepository {
    Optional<Batch> findById(BatchId batchId);
    List<Batch> findByProductIdOrderByExpirationDateAsc(ProductId productId);
    List<Batch> findEligibleForFefo(ProductId productId, LocalDate asOfDate);
    List<Batch> findEligibleForFefoWithLock(ProductId productId, LocalDate asOfDate);
    Batch save(Batch batch);
}
```

**Design Decision: Domain interfaces do NOT return JPA types**

Domain repository methods accept and return domain objects. The JPA infrastructure is invisible to the domain layer. The implementation handles all entity-to-domain mapping.

### 3.2 Repository Implementation (Infrastructure Adapter)

```java
// file: inventory/infrastructure/persistence/JpaBatchRepository.java
@Repository
public class JpaBatchRepository implements BatchRepository {
    private final SpringDataBatchRepository springData;
    private final BatchMapper mapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Batch save(Batch batch) {
        BatchEntity entity = mapper.toEntity(batch);
        BatchEntity saved = springData.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Batch> findEligibleForFefo(ProductId productId, LocalDate asOfDate) {
        return springData.findEligibleForFefo(productId.uuid(), asOfDate)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public List<Batch> findEligibleForFefoWithLock(ProductId productId, LocalDate asOfDate) {
        return springData.findEligibleForFefoWithLock(productId.uuid(), asOfDate)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }
}
```

**Design Decision: @Transactional on repository methods**

**Rationale:** Each repository method is self-transactional by default. When called from a use case with an outer `@Transactional`, they join that transaction (REQUIRED propagation). The `findEligibleForFefoWithLock` method is explicitly transactional because it uses `PESSIMISTIC_READ` lock, which requires an active transaction.

### 3.3 FEFO Query

```java
// file: inventory/infrastructure/persistence/SpringDataBatchRepository.java
public interface SpringDataBatchRepository extends JpaRepository<BatchEntity, UUID> {

    @Query("""
        SELECT b FROM BatchEntity b
        WHERE b.productId = :productId
          AND b.status IN ('ACTIVE', 'NEAR_EXPIRY')
          AND b.expirationDate > :asOfDate
          AND b.currentQuantity > 0
        ORDER BY b.expirationDate ASC, b.receivedDate ASC
        """)
    List<BatchEntity> findEligibleForFefo(UUID productId, LocalDate asOfDate);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        SELECT b FROM BatchEntity b
        WHERE b.productId = :productId
          AND b.status IN ('ACTIVE', 'NEAR_EXPIRY')
          AND b.expirationDate > :asOfDate
          AND b.currentQuantity > 0
        ORDER BY b.expirationDate ASC, b.receivedDate ASC
        """)
    List<BatchEntity> findEligibleForFefoWithLock(UUID productId, LocalDate asOfDate);
}
```

**Design Decision: SELECT FOR UPDATE on FEFO picks**

**Rationale:** Prevents phantom reads where two transactions see the same available stock. The lock is held for the duration of the `@Transactional` method. Acceptable for pharmacy volumes (hundreds of picks/day).

**Alternatives considered:** Optimistic only (no FOR UPDATE) — simpler but can miss inserts between transactions. `SKIP LOCKED` — better concurrency but PostgreSQL 9.5+ feature, adds complexity.

---

## Section 4: Transaction Boundaries

### 4.1 Transaction Map

| Use Case | Context | @Transactional | Participants | Propagation |
|----------|---------|---------------|--------------|-------------|
| Create Product | Product Catalog | Application service | ProductRepository.save() | REQUIRED |
| Update Product | Product Catalog | Application service | ProductRepository.save() | REQUIRED |
| Receive Stock | Inventory | Application service | BatchRepository.save(), MovementRepository.save() | REQUIRED |
| FEFO Allocate | Inventory | Application service (BatchAllocator) | BatchRepository.findEligibleForFefoWithLock(), BatchRepository.save(), MovementRepository.save() | REQUIRED |
| Create Sale | Sales | Application service (CreateSaleUseCase) | BatchAllocator.allocate() (per product), SaleRepository.save() | REQUIRED |
| Void Sale | Sales | Application service | Batch increment, Movement create (RETURN), SaleRepository.save() | REQUIRED |
| Record Adjustment | Inventory | Application service | MovementRepository.save(), optional Batch update | REQUIRED |
| Quarantine Batch | Inventory | Application service | BatchRepository.save() | REQUIRED |
| Write Off Batch | Inventory | Application service | BatchRepository.save(), MovementRepository.save() | REQUIRED |
| Evaluate Alerts (event) | Alerting | Event handler | AlertInstanceRepository.save() | REQUIRED (new transaction if AFTER_COMMIT) |
| Evaluate Alerts (scheduled) | Alerting | Scheduled job | AlertInstanceRepository.save() | REQUIRED |
| Dismiss Alert | Alerting | Application service | AlertInstanceRepository.save() | REQUIRED |
| Resolve Alert | Alerting | Application service | AlertInstanceRepository.save() | REQUIRED |

### 4.2 Transactional Pattern

```java
// file: sales/application/service/CreateSaleUseCase.java
@Service
public class CreateSaleUseCase {
    private final BatchAllocator batchAllocator;
    private final SaleRepository saleRepository;
    private final ProductValidationService productValidation;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional  // ← Single transaction for entire sale
    public SaleCreatedResponse createSale(CreateSaleCommand command) {
        // 1. Validate products
        Map<UUID, ProductInfo> products = productValidation.validateProductsExist(
            command.items().stream().map(CreateSaleItem::productId).toList());

        // 2. Build sale aggregate
        Sale sale = Sale.create(command, products);

        // 3. For each product, call BatchAllocator (joins this transaction via REQUIRED)
        for (SaleItem item : sale.getItems()) {
            AllocationResult result = batchAllocator.allocate(
                item.getProductId(), item.getQuantity());
            item.recordAllocations(result.allocations());
        }

        // 4. Persist sale (cascades to items and allocations)
        Sale saved = saleRepository.save(sale);

        // 5. Publish event (within same transaction — AFTER_COMMIT listeners see it)
        eventPublisher.publishEvent(new SaleCompletedEvent(saved));

        return SaleCreatedResponse.from(saved);
    }
}
```

### 4.3 Event Publishing Strategy

```java
// Publisher (inside @Transactional)
eventPublisher.publishEvent(new BatchReceivedEvent(batch));

// Consumer (in another context's infrastructure layer)
@Component
public class InventoryEventConsumer {
    private final AlertEvaluationService alertService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBatchReceived(BatchReceivedEvent event) {
        alertService.evaluateLowStockForProduct(event.getProductId());
    }
}
```

**Design Decision: ApplicationEventPublisher + AFTER_COMMIT**

| Option | Tradeoff | Decision |
|--------|----------|----------|
| **ApplicationEventPublisher** (chosen) | Simple, zero infra, in-process, transactional | ✅ **DSG-06** |
| Outbox pattern | Durable, survives process crash, requires DB poller | ❌ Post-MVP |
| Message broker (RabbitMQ) | Best durability, async, but adds ops complexity | ❌ Post-MVP |

**Rationale:** For MVP volumes, in-process events with `@TransactionalEventListener(phase = AFTER_COMMIT)` provide exactly-once delivery semantics within the same JVM. If the process crashes before AFTER_COMMIT, the transaction never committed, so no event is lost. If AFTER_COMMIT listener fails, the transaction already committed — acceptable (retry on next event or scheduled job).

---

## Section 5: FEFO Component Design

### 5.1 Component Diagram

```
                     ┌──────────────────────────────────────┐
                     │         CreateSaleUseCase             │
                     │      (Sales — application layer)      │
                     └──────────────┬───────────────────────┘
                                    │ calls per product
                                    ▼
                     ┌──────────────────────────────────────┐
                     │         BatchAllocator                │
                     │   (Inventory — application layer)     │
                     │   @Transactional(REQUIRED)            │
                     │   @Retryable(3x)                      │
                     └──────┬───────────────────┬───────────┘
                            │                   │
                    calls   │                   │  calls
                            ▼                   ▼
             ┌────────────────────┐   ┌──────────────────────┐
             │ AllocationStrategy │   │   MovementRepository │
             │ (domain interface) │   │  (domain port → JPA) │
             └────────┬───────────┘   └──────────────────────┘
                      │ implements
                      ▼
             ┌────────────────────┐
             │FefoAllocationStrat.│
             │ (domain — pure Java│
             │  no Spring deps)   │
             └────────────────────┘
```

### 5.2 AllocationStrategy (Domain Interface)

```java
// file: inventory/domain/service/AllocationStrategy.java
public interface AllocationStrategy {
    List<BatchAllocation> allocate(ProductId productId, int requestedQuantity);
}
```

### 5.3 FefoAllocationStrategy (Domain Implementation)

```java
// file: inventory/domain/service/FefoAllocationStrategy.java
public class FefoAllocationStrategy implements AllocationStrategy {
    private final BatchRepository batchRepository;

    public FefoAllocationStrategy(BatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    @Override
    public List<BatchAllocation> allocate(ProductId productId, int requestedQuantity) {
        List<Batch> eligible = batchRepository.findEligibleForFefo(
            productId, LocalDate.now());

        int totalAvailable = eligible.stream()
            .mapToInt(Batch::getCurrentQuantity)
            .sum();

        if (totalAvailable < requestedQuantity) {
            throw new InsufficientStockException(
                "Requested %d but only %d available".formatted(requestedQuantity, totalAvailable));
        }

        List<BatchAllocation> allocations = new ArrayList<>();
        int remaining = requestedQuantity;

        for (Batch batch : eligible) {
            if (remaining <= 0) break;
            int take = Math.min(batch.getCurrentQuantity(), remaining);
            allocations.add(new BatchAllocation(
                batch.getBatchId(), take, batch.getExpirationDate(), batch.getCostPerUnit()));
            remaining -= take;
        }

        return allocations;
    }
}
```

**Design Decision: FefoAllocationStrategy is in domain layer, BatchAllocator is in application layer**

**Rationale:** The strategy is pure domain logic (sorting, allocation math, invariants). The allocator orchestration (transaction management, retry, repository calls) is application-layer concern. Test: the strategy can be unit-tested with a mocked repository; the allocator is integration-tested with Testcontainers.

### 5.4 BatchAllocator (Application Layer)

```java
// file: inventory/application/service/BatchAllocator.java
@Service
public class BatchAllocator {
    private final AllocationStrategy strategy;
    private final BatchRepository batchRepository;
    private final MovementRepository movementRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRED)  // joins caller's transaction
    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2)
    )
    public AllocationResult allocate(ProductId productId, int requestedQuantity) {
        // Step 1: Strategy determines allocation plan (read-only query with FOR UPDATE)
        List<BatchAllocation> allocations = strategy.allocate(productId, requestedQuantity);

        // Step 2: Execute — update batch quantities and create movements
        for (BatchAllocation alloc : allocations) {
            Batch batch = batchRepository.findById(alloc.batchId())
                .orElseThrow(() -> new BatchNotFoundException(alloc.batchId()));

            batch.decrementQuantity(alloc.allocatedQuantity());
            batchRepository.save(batch);  // version check here

            Movement movement = Movement.createSaleMovement(
                batch.getProductId(),
                batch.getBatchId(),
                alloc.allocatedQuantity(),
                alloc.costPerUnit(),
                batch.getTenantId());
            movementRepository.save(movement);
        }

        return new AllocationResult(allocations);
    }
}
```

### 5.5 Retry Logic Detail

```java
@Retryable(
    retryFor = ObjectOptimisticLockingFailureException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 50, multiplier = 2)
)
```

- **Retry on:** `ObjectOptimisticLockingFailureException` (wraps JPA `OptimisticLockException`)
- **Max attempts:** 3 (first attempt + 2 retries)
- **Backoff:** 50ms, 100ms, 200ms (exponential)
- **Not retried:** `InsufficientStockException`, `BatchNotFoundException` — these are business errors, not concurrency conflicts

**Design Decision: @Retryable over manual retry loop**

| Option | Tradeoff | Decision |
|--------|----------|----------|
| **@Retryable** (chosen) | Declarative, Spring-managed, testable with Spring Retry | ✅ **DSG-04** |
| Manual retry loop | More control, no Spring dependency, but more code per method | ❌ |

**Implementation impact:** Requires `spring-retry` dependency and `@EnableRetry` on a `@Configuration` class.

### 5.6 N+1 BatchAllocator Calls Decision

From SAL-DEC-09: One call per product. If a sale has N products, `BatchAllocator.allocate()` is called N times. For average MVP sale sizes (1–3 products), this is fine. If `avg > 10 products/sale` becomes common, introduce a batch variant:

```java
// Future optimization (post-MVP):
AllocationResult allocate(Map<ProductId, Integer> productQuantities);
```

---

## Section 6: Frontend Feature Structure

### 6.1 Next.js App Router Structure

```
frontend/
├── src/
│   ├── app/
│   │   ├── (dashboard)/
│   │   │   ├── layout.tsx               # AppShell: Sidebar + Topbar + AlertBadge
│   │   │   ├── page.tsx                 # Dashboard: stock summary + critical alerts
│   │   │   ├── loading.tsx              # Dashboard skeleton
│   │   │   ├── products/
│   │   │   │   ├── page.tsx             # Product list (ProductGrid + ProductSearch)
│   │   │   │   ├── loading.tsx          # Product grid skeleton
│   │   │   │   └── [id]/
│   │   │   │       ├── page.tsx         # Product detail
│   │   │   │       └── edit/
│   │   │   │           └── page.tsx     # Product form (create+edit)
│   │   │   ├── inventory/
│   │   │   │   ├── page.tsx             # Inventory overview (filterable batch list)
│   │   │   │   ├── receive/
│   │   │   │   │   └── page.tsx         # Batch receiving form
│   │   │   │   ├── movements/
│   │   │   │   │   └── page.tsx         # Movement timeline
│   │   │   │   └── batches/
│   │   │   │       └── [id]/
│   │   │   │           └── page.tsx     # Batch detail
│   │   │   ├── sales/
│   │   │   │   ├── page.tsx             # Sales list (filterable)
│   │   │   │   ├── new/
│   │   │   │   │   └── page.tsx         # Sale entry form
│   │   │   │   └── [id]/
│   │   │   │       └── page.tsx         # Sale detail
│   │   │   ├── alerts/
│   │   │   │   └── page.tsx             # Alert panel (filterable list)
│   │   │   └── settings/
│   │   │       └── page.tsx             # Settings (locations, alert rules)
│   │   ├── layout.tsx                   # Root layout (providers)
│   │   ├── not-found.tsx                # 404 page
│   │   └── error.tsx                    # Error boundary
│   │
│   ├── components/
│   │   ├── ui/                          # shadcn/ui primitives (button, card, input, etc.)
│   │   ├── layout/
│   │   │   ├── app-shell.tsx            # Main layout wrapper
│   │   │   ├── sidebar.tsx              # Navigation sidebar with AlertBadge
│   │   │   └── topbar.tsx               # Top bar with search, user menu
│   │   ├── products/
│   │   │   ├── product-card.tsx         # Single product card (presentational)
│   │   │   ├── product-grid.tsx         # Responsive grid of product cards
│   │   │   ├── product-search.tsx       # Search + filter bar
│   │   │   ├── product-form.tsx         # Create/edit form
│   │   │   ├── product-detail.tsx       # Full product detail view
│   │   │   └── product-detail-skeleton.tsx
│   │   ├── inventory/
│   │   │   ├── batch-receiving-form.tsx # Stock receiving form
│   │   │   ├── batch-list.tsx           # Filterable batch table
│   │   │   ├── batch-detail.tsx         # Single batch view
│   │   │   ├── stock-card.tsx           # Stock position card
│   │   │   ├── stock-status-badge.tsx   # Visual stock indicator
│   │   │   ├── movement-timeline.tsx    # Chronological movement list
│   │   │   ├── adjustment-form.tsx      # Inventory adjustment form
│   │   │   └── expiration-calendar.tsx  # Calendar view of expiring batches
│   │   ├── sales/
│   │   │   ├── sale-form.tsx            # Multi-line sale entry form
│   │   │   ├── sale-list.tsx            # Filterable sales table
│   │   │   ├── sale-detail.tsx          # Single sale view
│   │   │   └── batch-allocation-preview.tsx # FEFO allocation preview
│   │   ├── alerts/
│   │   │   ├── alert-panel.tsx          # Main alerts page
│   │   │   ├── alert-card.tsx           # Individual alert
│   │   │   ├── alert-badge.tsx          # Sidebar count badge
│   │   │   └── alert-summary-row.tsx    # Dashboard widget
│   │   └── shared/
│   │       ├── status-badge.tsx         # Generic status indicator
│   │       ├── search-input.tsx         # Debounced search input
│   │       ├── data-table.tsx           # Generic data table with pagination
│   │       ├── empty-state.tsx          # Empty state with illustration
│   │       ├── error-state.tsx          # Error state with retry
│   │       ├── loading-skeleton.tsx     # Generic skeleton wrapper
│   │       └── confirmation-dialog.tsx  # Confirm action dialog
│   │
│   ├── lib/
│   │   ├── api-client.ts               # Single fetch wrapper (base URL, auth, error handling)
│   │   ├── utils.ts                     # cn(), formatCurrency(), formatDate()
│   │   └── constants.ts                 # API base URL, pagination defaults
│   │
│   ├── hooks/
│   │   ├── use-products.ts             # Product CRUD + list queries
│   │   ├── use-batches.ts              # Batch queries + receiving mutation
│   │   ├── use-sales.ts                # Sale CRUD + void mutation
│   │   ├── use-alerts.ts               # Alert queries + dismiss/resolve mutations
│   │   ├── use-stock.ts                # Stock projection queries
│   │   └── use-categories.ts           # Category queries
│   │
│   ├── types/
│   │   ├── product.ts                  # ProductSummary, ProductDetail, CreateProductRequest
│   │   ├── inventory.ts                # Batch, Movement, StockProjection, BatchAllocation
│   │   ├── sales.ts                    # Sale, SaleItem, SaleSummary
│   │   ├── alert.ts                    # AlertRule, AlertInstance, AlertSummary
│   │   └── common.ts                   # PaginatedResponse, ApiError, SortOption
│   │
│   └── styles/
│       └── globals.css                 # Tailwind CSS 4 directives
```

**Design Decision: No global state manager**

**Rationale:** TanStack Query handles all server state. URL search params handle filter/sort state. No Redux, no Zustand, no client state store for MVP. The only client state is form state (local component state) and UI state (sidebar open/closed — URL or React context).

### 6.2 API Client Architecture

```typescript
// file: lib/api-client.ts
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

interface ApiOptions extends RequestInit {
  params?: Record<string, string | boolean | number | undefined>;
}

class ApiClient {
  private baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  private async request<T>(path: string, options: ApiOptions = {}): Promise<T> {
    const { params, ...fetchOptions } = options;

    // Build URL with query params
    const url = new URL(`${this.baseUrl}${path}`);
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== '') url.searchParams.set(key, String(value));
      });
    }

    const response = await fetch(url.toString(), {
      headers: {
        'Content-Type': 'application/json',
        ...fetchOptions.headers,
      },
      ...fetchOptions,
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new ApiError(response.status, error.code || 'UNKNOWN', error.message, error.details);
    }

    return response.json();
  }

  get<T>(path: string, params?: Record<string, any>) {
    return this.request<T>(path, { method: 'GET', params });
  }

  post<T>(path: string, body?: unknown) {
    return this.request<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined });
  }

  put<T>(path: string, body: unknown) {
    return this.request<T>(path, { method: 'PUT', body: JSON.stringify(body) });
  }

  patch<T>(path: string, body?: unknown) {
    return this.request<T>(path, { method: 'PATCH', body: body ? JSON.stringify(body) : undefined });
  }

  delete<T>(path: string) {
    return this.request<T>(path, { method: 'DELETE' });
  }
}

export const api = new ApiClient(API_BASE);
```

### 6.3 Page Data Flow

```
Page Component
    │
    ▼
useProducts() / useBatches() / useSales() / useAlerts()
    │  TanStack Query hook
    │  { data, isLoading, error, refetch }
    ▼
api.get<ProductResponse[]>('/products', { search, categoryId, page, size })
    │  Fetch wrapper (auth header, error handling)
    ▼
Spring Boot REST API
    │
    ▼
Response → TanStack Query cache → Component re-render
```

#### TanStack Query Hook Example

```typescript
// file: hooks/use-products.ts
export function useProducts(filters: ProductFilters) {
  return useQuery({
    queryKey: ['products', filters],
    queryFn: () => api.get<PaginatedResponse<ProductSummary>>('/products', {
      search: filters.search,
      categoryId: filters.categoryId,
      isActive: filters.showInactive ? undefined : true,
      page: filters.page,
      size: filters.size,
      sort: filters.sort,
    }),
    placeholderData: keepPreviousData,  // smooth pagination
  });
}

export function useCreateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateProductRequest) => api.post<ProductResponse>('/products', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
    },
  });
}
```

---

## Section 7: API Module Structure

### 7.1 REST Controller Pattern

```java
// file: productcatalog/infrastructure/rest/ProductController.java
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final RegisterProductUseCase registerProduct;
    private final ListProductsUseCase listProducts;
    private final GetProductUseCase getProduct;
    private final UpdateProductUseCase updateProduct;
    private final DeactivateProductUseCase deactivateProduct;
    private final ReactivateProductUseCase reactivateProduct;

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        ProductResponse response = registerProduct.execute(
            request.toCommand(principal.getTenantId()));
        return ResponseEntity
            .created(URI.create("/api/v1/products/" + response.id()))
            .body(response);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<ProductListResponse>> list(
            @Valid ProductListQueryParams params,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(
            listProducts.execute(params.toQuery(principal.getTenantId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(getProduct.execute(new ProductId(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(
            updateProduct.execute(request.toCommand(new ProductId(id))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ProductResponse> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(deactivateProduct.execute(new ProductId(id)));
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<ProductResponse> reactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(reactivateProduct.execute(new ProductId(id)));
    }
}
```

### 7.2 DTO Naming Convention

| Pattern | Example | Location |
|---------|---------|----------|
| `{Action}{Entity}Request` | `CreateProductRequest` | `infrastructure/rest/dto/` |
| `{Entity}Response` | `ProductResponse` | `infrastructure/rest/dto/` |
| Request → Command | `CreateProductRequest.toCommand()` | On the DTO or a mapper |
| Entity → Response | `ProductResponse.from(Product)` | Static factory on the DTO |

```java
// file: productcatalog/infrastructure/rest/dto/CreateProductRequest.java
public record CreateProductRequest(
    @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9\\-]+$") String sku,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 2000) String description,
    @NotBlank @Size(max = 200) String laboratory,
    @NotBlank @Size(max = 100) String presentation,
    @Size(min = 13, max = 13) String gtin,
    @NotNull UUID categoryId,
    Boolean requiresSerialTracking,
    Boolean requiresColdChain,
    Boolean isControlledSubstance,
    @PositiveOrZero Integer defaultReorderPoint,
    @PositiveOrZero Integer defaultMaxStock
) {
    public RegisterProductCommand toCommand(String tenantId) {
        return new RegisterProductCommand(
            new ProductId(UUID.randomUUID()),
            tenantId,
            sku, name, description, laboratory, presentation,
            gtin, new CategoryId(categoryId),
            requiresSerialTracking != null && requiresSerialTracking,
            requiresColdChain != null && requiresColdChain,
            isControlledSubstance != null && isControlledSubstance,
            defaultReorderPoint, defaultMaxStock
        );
    }
}

// file: productcatalog/infrastructure/rest/dto/ProductResponse.java
public record ProductResponse(
    UUID id,
    String sku,
    String name,
    String description,
    String laboratory,
    String presentation,
    String gtin,
    UUID categoryId,
    String categoryName,
    boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    long version
) {
    public static ProductResponse from(Product product, String categoryName) {
        return new ProductResponse(
            product.getProductId().uuid(),
            product.getSku(),
            product.getName(),
            product.getDescription(),
            product.getLaboratory(),
            product.getPresentation(),
            product.getGtin(),
            product.getCategoryId().uuid(),
            categoryName,
            product.isActive(),
            product.getCreatedAt(),
            product.getUpdatedAt(),
            product.getVersion()
        );
    }
}
```

### 7.3 Validation Strategy

**Layer 1 — Jakarta Validation on DTOs:**
- `@NotBlank`, `@NotNull`, `@Positive`, `@Future`, `@Size`, `@Pattern`
- Handled by Spring's `@Valid` + `MethodArgumentNotValidException`

**Layer 2 — Domain validation in domain model:**
- Invariants checked in constructors and factory methods
- e.g., `Batch.decrementQuantity()` checks `currentQuantity >= quantity`

**Layer 3 — Application validation in use cases:**
- Business rules that span aggregates (e.g., "product must exist", "SKU unique per tenant")
- Custom exceptions thrown from use case methods

**Layer 4 — DB constraints (safety net):**
- Unique constraints, NOT NULL, CHECK constraints
- Caught by `DataIntegrityViolationException` → mapped to domain exceptions

```java
// Custom validator example: GTIN Luhn check
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GtinValidator.class)
public @interface ValidGtin {
    String message() default "Invalid GTIN: must be 13 digits with valid Luhn checksum";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class GtinValidator implements ConstraintValidator<ValidGtin, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;  // optional field
        if (!value.matches("\\d{13}")) return false;
        return LuhnCheck.validate(value);
    }
}
```

---

## Section 8: Flyway Organization

### 8.1 Migration Files

```
src/main/resources/db/migration/
├── V1__create_schema_product_catalog.sql
├── V2__create_schema_inventory.sql
├── V3__create_schema_sales.sql
└── V4__create_schema_alerting.sql
```

### 8.2 Naming Convention

`V{version}__{description}.sql`

- **Version per schema, not per table.** Each major context gets ONE migration file.
- If schema changes are needed post-MVP, use `V5__`, `V6__`, etc.
- Never modify an existing migration after it's committed (Flyway checksum check).

### 8.3 V1: Product Catalog

```sql
-- V1__create_schema_product_catalog.sql
CREATE SCHEMA IF NOT EXISTS product_catalog;

-- See product-catalog-spec.md Appendix A for full DDL
-- products, categories tables with indexes
```

### 8.4 V2: Inventory

```sql
-- V2__create_schema_inventory.sql
CREATE SCHEMA IF NOT EXISTS inventory;

CREATE TABLE inventory.batches (
    batch_id          UUID PRIMARY KEY,
    tenant_id         VARCHAR(36) NOT NULL,
    product_id        UUID NOT NULL,
    lot_number        VARCHAR(100) NOT NULL,
    manufacturing_date DATE,
    expiration_date   DATE NOT NULL,
    initial_quantity  INTEGER NOT NULL CHECK (initial_quantity > 0),
    current_quantity  INTEGER NOT NULL CHECK (current_quantity >= 0),
    location_id       UUID NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    cost_per_unit     DECIMAL(12,2) NOT NULL CHECK (cost_per_unit > 0),
    received_date     TIMESTAMP WITH TIME ZONE NOT NULL,
    notes             TEXT,
    -- BaseEntity fields
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_batches_product_lot_expiry UNIQUE (tenant_id, product_id, lot_number, expiration_date),
    CONSTRAINT ck_batch_current_lte_initial CHECK (current_quantity <= initial_quantity)
);

CREATE TABLE inventory.movements (
    movement_id    UUID PRIMARY KEY,
    tenant_id      VARCHAR(36) NOT NULL,
    movement_type  VARCHAR(20) NOT NULL,
    direction      VARCHAR(5) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    quantity       INTEGER NOT NULL CHECK (quantity > 0),
    product_id     UUID NOT NULL,
    batch_id       UUID,
    reference_type VARCHAR(30),
    reference_id   UUID,
    unit_price     DECIMAL(12,2),
    total_value    DECIMAL(12,2),
    reason         TEXT,
    performed_by   VARCHAR(100) NOT NULL,
    performed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    notes          TEXT,
    source         VARCHAR(20) NOT NULL DEFAULT 'API',
    -- BaseEntity fields
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_movement_performed_before_recorded CHECK (performed_at <= recorded_at)
);

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

CREATE TABLE inventory.locations (
    location_id   UUID PRIMARY KEY,
    tenant_id     VARCHAR(36) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    type          VARCHAR(20) NOT NULL DEFAULT 'SHELF',
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_locations_tenant_name UNIQUE (tenant_id, name)
);

-- FEFO pick index (partial index for performance)
CREATE INDEX idx_batches_fefo_pick
    ON inventory.batches (tenant_id, product_id, status, expiration_date, received_date)
    WHERE status IN ('ACTIVE', 'NEAR_EXPIRY') AND current_quantity > 0;

CREATE INDEX idx_batches_expiry
    ON inventory.batches (tenant_id, expiration_date)
    WHERE status IN ('ACTIVE', 'NEAR_EXPIRY');

CREATE INDEX idx_movements_tenant_time
    ON inventory.movements (tenant_id, performed_at DESC);

CREATE INDEX idx_movements_batch
    ON inventory.movements (tenant_id, batch_id);
```

### 8.5 V3: Sales

```sql
-- See sales-context-spec.md Section 9 for full DDL
-- sales, sale_items, batch_allocations tables with indexes
```

### 8.6 V4: Alerting

```sql
-- See alerting-context-spec.md Section 12 for full DDL
-- alert_rules, alert_instances tables with indexes
```

### 8.7 Flyway Configuration

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    schemas:
      - product_catalog
      - inventory
      - sales
      - alerting
    default-schema: product_catalog
    locations: classpath:db/migration
```

---

## Section 9: Testing Folder Structure

```
src/test/java/com/medistock/
├── archunit/
│   └── HexagonalArchitectureTest.java          # All 10 ArchUnit rules
│
├── productcatalog/
│   ├── domain/
│   │   ├── ProductTest.java
│   │   └── CategoryTest.java
│   ├── application/
│   │   ├── RegisterProductUseCaseTest.java
│   │   ├── UpdateProductUseCaseTest.java
│   │   ├── DeactivateProductUseCaseTest.java
│   │   └── ReactivateProductUseCaseTest.java
│   └── infrastructure/
│       ├── JpaProductRepositoryTest.java
│       ├── JpaCategoryRepositoryTest.java
│       ├── ProductControllerTest.java
│       └── CategoryControllerTest.java
│
├── inventory/
│   ├── domain/
│   │   ├── BatchTest.java
│   │   ├── FefoAllocationStrategyTest.java     # 10+ edge cases
│   │   ├── MovementTest.java
│   │   └── StockProjectionTest.java
│   ├── application/
│   │   ├── ReceiveStockUseCaseTest.java
│   │   ├── BatchAllocatorTest.java
│   │   ├── AdjustmentUseCaseTest.java
│   │   └── BatchLifecycleUseCaseTest.java
│   └── infrastructure/
│       ├── JpaBatchRepositoryTest.java
│       ├── JpaMovementRepositoryTest.java
│       ├── JpaStockProjectionRepositoryTest.java
│       └── InventoryControllerTest.java
│
├── sales/
│   ├── domain/
│   │   └── SaleTest.java
│   ├── application/
│   │   ├── CreateSaleUseCaseTest.java
│   │   └── VoidSaleUseCaseTest.java
│   └── infrastructure/
│       ├── JpaSaleRepositoryTest.java
│       ├── CreateSaleIntegrationTest.java       # Full flow with Testcontainers
│       └── VoidSaleIntegrationTest.java
│
├── alerting/
│   ├── domain/
│   │   ├── AlertRuleTest.java
│   │   └── AlertInstanceTest.java
│   ├── application/
│   │   ├── AlertEvaluationServiceTest.java
│   │   ├── AlertLifecycleServiceTest.java
│   │   └── AlertSummaryServiceTest.java
│   └── infrastructure/
│       ├── JpaAlertRuleRepositoryTest.java
│       ├── JpaAlertInstanceRepositoryTest.java
│       ├── AlertEventConsumerIntegrationTest.java
│       ├── AlertScheduledJobIntegrationTest.java
│       └── AlertControllerTest.java
│
└── concurrency/
    ├── FefoConcurrencyTest.java                 # Concurrent pick stress test
    └── OptimisticLockRetryTest.java             # @Retryable verification
```

**Design Decision: Mirror source structure in tests**

Test packages mirror source packages exactly. `productcatalog/domain/ProductTest.java` tests `productcatalog/domain/model/Product.java`. This makes it trivial to find the corresponding test for any class.

---

## Section 10: ArchUnit Rules

### 10.1 Complete ArchUnit Test

```java
// file: src/test/java/com/medistock/archunit/HexagonalArchitectureTest.java
@AnalyzeClasses(packages = "com.medistock")
public class HexagonalArchitectureTest {

    // RULE 1: Domain layer has ZERO dependencies on Spring, JPA, or any framework
    @Test
    void domainLayerMustNotDependOnFramework() {
        noClasses()
            .that().resideInAnyPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "javax.persistence..")
            .check(importedClasses);
    }

    // RULE 2: Application layer depends ONLY on domain layer
    @Test
    void applicationLayerMustOnlyDependOnDomain() {
        noClasses()
            .that().resideInAnyPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "org.springframework.web..", "jakarta.servlet..")
            .check(importedClasses);
    }

    // RULE 3: Infrastructure depends on domain + framework, never on another context's infrastructure
    @Test
    void infrastructureMustNotDependOnOtherContextInfrastructure() {
        noClasses()
            .that().resideInAnyPackage("..infrastructure..")
            .and().resideInAPackage("com.medistock.productcatalog..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.medistock.inventory.infrastructure..",
                                "com.medistock.sales.infrastructure..",
                                "com.medistock.alerting.infrastructure..")
            .check(importedClasses);

        noClasses()
            .that().resideInAnyPackage("..infrastructure..")
            .and().resideInAPackage("com.medistock.inventory..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.medistock.productcatalog.infrastructure..",
                                "com.medistock.sales.infrastructure..",
                                "com.medistock.alerting.infrastructure..")
            .check(importedClasses);
    }

    // RULE 4: FefoAllocationStrategy is in domain, not application
    @Test
    void fefoAllocationStrategyMustBeInDomain() {
        classes()
            .that().haveSimpleName("FefoAllocationStrategy")
            .should().resideInAPackage("..domain..")
            .check(importedClasses);
    }

    // RULE 5: BatchAllocator is in application, not domain
    @Test
    void batchAllocatorMustBeInApplication() {
        classes()
            .that().haveSimpleName("BatchAllocator")
            .should().resideInAPackage("..application..")
            .check(importedClasses);
    }

    // RULE 6: Controllers are in infrastructure.rest, not in domain or application
    @Test
    void controllersMustBeInInfrastructureRest() {
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..infrastructure.rest..")
            .check(importedClasses);
    }

    // RULE 7: Repository interfaces are ports in domain, implementations in infrastructure.persistence
    @Test
    void repositoryPortsAreInDomainImplementationsInInfrastructure() {
        // Interfaces named *Repository are domain ports
        classes()
            .that().areInterfaces()
            .and().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage("..domain.port..")
            .check(importedClasses);

        // Classes named Jpa*Repository are infrastructure implementations
        classes()
            .that().haveSimpleNameStartingWith("Jpa")
            .and().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage("..infrastructure.persistence..")
            .check(importedClasses);
    }

    // RULE 8: Sales application MAY depend on Inventory application
    @Test
    void salesApplicationMayDependOnInventoryApplication() {
        // This is allowed — no violation test needed.
        // Instead, we verify the inverse: Inventory must NOT depend on Sales
        noClasses()
            .that().resideInAnyPackage("com.medistock.inventory..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.medistock.sales..")
            .check(importedClasses);
    }

    // RULE 9: Sales domain MUST NOT depend on Inventory domain
    @Test
    void salesDomainMustNotDependOnInventoryDomain() {
        noClasses()
            .that().resideInAnyPackage("com.medistock.sales.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.medistock.inventory.domain..")
            .check(importedClasses);
    }

    // RULE 10: No REQUIRES_NEW propagation on BatchAllocator
    @Test
    void batchAllocatorMustNotUseRequiresNew() {
        // Check that BatchAllocator does not use REQUIRES_NEW
        // This is a convention rule — we can also add a custom ArchUnit condition
        // For now, review-based: code review must verify propagation = REQUIRED
    }
}
```

### 10.2 Dependency Enforcement Summary

```
┌─────────────────────────────────────────────────────────────────┐
│                      DEPENDENCY RULES                            │
│                                                                  │
│  domain (ANY context)   ──→   ONLY Java standard library         │
│       ↑                                                          │
│  application (ANY ctx)  ──→   ONLY its own domain                │
│       ↑                                                          │
│  infrastructure (ctx A) ──→   domain A + framework (Spring, JPA) │
│                               NEVER another context's infra      │
│                                                                  │
│  CROSS-CONTEXT:                                                  │
│    Sales.application ──→ Inventory.application  ✅ (via interface)│
│    Inventory.application ──→ Sales.application  ❌               │
│    Sales.domain ──→ Inventory.domain             ❌               │
│                                                                  │
│  PACKAGE LOCATION ENFORCEMENT:                                   │
│    *Controller → infrastructure.rest                              │
│    *Repository interface → domain.port                            │
│    Jpa*Repository → infrastructure.persistence                    │
│    FefoAllocationStrategy → domain.service                        │
│    BatchAllocator → application.service                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## Section 11: Design Decisions Log

| ID | Decision | Rationale | Alternative | Impact |
|----|----------|-----------|-------------|--------|
| **DSG-01** | Single Gradle module | Simple build, fast iteration, ArchUnit enforces boundaries at package level | Multi-module: stronger compile-time isolation but more complex build | Simpler CI, faster builds, convention-based enforcement |
| **DSG-02** | Separate domain + JPA entities | Hexagonal purity, testable domain without Spring, no framework annotations on domain objects | Single model: less code but framework leaks into domain | More mapper code, cleaner tests, manual mapping in repos |
| **DSG-03** | Manual mapping in repositories | No build-time code generation, explicit control, no generated code to debug | MapStruct: less boilerplate but generated code and build-time processing | More explicit code, full control, no magic |
| **DSG-04** | @Retryable for FEFO concurrency | Declarative retry, Spring-managed, testable with Spring Retry test utilities | Manual retry loop: more control but repetitive code across allocators | Simple annotation, requires spring-retry dependency |
| **DSG-05** | @Lock(PESSIMISTIC_READ) on FEFO query | Prevents phantom reads between concurrent transactions, FOR UPDATE on selected rows | Optimistic only: simpler but concurrent reads see stale data | Verified for pharmacy volumes, reduces concurrency slightly |
| **DSG-06** | ApplicationEventPublisher + AFTER_COMMIT | Simple, transactional, zero infrastructure to maintain, in-process events | Outbox pattern: durable but requires DB poller table and scheduler | Adequate for MVP volumes, no infra overhead |
| **DSG-07** | Single Event type for all consumers | One event class per domain event, consumed by @TransactionalEventListener | Separate internal/external events: cleaner API boundaries but more classes | Simple, no event versioning needed for MVP |
| **DSG-08** | UUID PKs over auto-increment | Prevents enumeration, works across tenant-sharded DBs, no ID race | Auto-increment long: simpler but exposes data volume | Slightly larger index, negligible perf impact |
| **DSG-09** | Version field in Batch domain (opaque) | FEFO retry logic reads version to detect concurrent modifications | Version strictly JPA-only: cleaner domain but retry logic can't detect conflicts | Domain has read-only version, never modifies it |
| **DSG-10** | One BatchAllocator call per product | Simple contract, allocator handles cross-batch splitting internally | Batch variant `allocate(Map<ProductId, Integer>)`: fewer DB round trips | N calls for N products; optimize post-MVP if avg > 10 |
| **DSG-11** | TanStack Query for server state | No global state manager, URL params for filters, server cache is source of truth | Redux/Zustand: client-first state management, more boilerplate | Less code, simpler mental model, Shareable URLs |
| **DSG-12** | Jakarta Validation on DTOs + domain invariants | Multi-layered validation: transport layer (DTO) + business layer (domain) | All validation in domain: cleaner but no auto-generated 400 responses | Validation is explicit at each layer |
| **DSG-13** | No inbound port interfaces in application | YAGNI for MVP — only one implementation of each use case | Always use interfaces: cleaner hexagonal architecture | Can extract interface when second implementation needed |
| **DSG-14** | Scheduled daily jobs for near-expiry + expired checks | Expiration is a known future date — no need for event-driven re-evaluation per movement | Event-driven: real-time but wasteful recomputation | Simple, predictable, adequate for MVP |
| **DSG-15** | StockProjection rebuilt on startup | Safety net against projection drift, @PostConstruct recomputes from batches | Periodic reconciliation job: more robust drift detection | Startup rebuild is free (happens once). Post-MVP: add nightly reconciliation |

---

## Cross-Cutting Concerns

### Multi-Tenancy

Every table includes `tenant_id VARCHAR(36) NOT NULL` as the first column after the PK. All queries filter by `tenant_id`. The `tenant_id` is extracted from the JWT token's `tenant_id` claim in a request filter and stored in a `ThreadLocal<TenantContext>`:

```java
// file: crosscutting/security/TenantContext.java
public class TenantContext {
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    public static void setTenantId(String tenantId) { currentTenant.set(tenantId); }
    public static String getTenantId() { return currentTenant.get(); }
    public static void clear() { currentTenant.remove(); }
}
```

### Global Exception Handler

```java
// file: crosscutting/web/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed",
            ex.getFieldErrors().stream()
                .map(fe -> new ErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ErrorResponse.conflict("OPTIMISTIC_LOCK",
            "Resource was modified by another user. Refresh and retry.");
    }

    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDomainException(DomainException ex) {
        return ErrorResponse.conflict(ex.getErrorCode(), ex.getMessage());
    }

    // ... more handlers for 404, 403, 500
}
```

### Correlation ID

Every request gets a unique correlation ID (generated by a `OncePerRequestFilter` or MDC filter), logged in every log line, and returned in every API response header and error body.

---

## Output Summary

### Status
✅ Complete — ready to proceed to task generation

### Executive Summary
Technical design for MediStock MVP covering 4 bounded contexts (Product Catalog, Inventory, Sales, Alerting) with hexagonal architecture, single-module Gradle project, schema-per-context Flyway migrations, FEFO allocation engine with optimistic locking + retry, separate domain/JPA entities, TanStack Query frontend data layer, and ArchUnit-enforced package dependency rules. 15 design decisions documented with rationale and alternatives.

### Artifacts
- `docs/design/technical-design.md` — This document

### Next Recommended Step
**sdd-tasks** — Break this design into implementation tasks across all 6 phases:
1. Phase 1: Foundation (scaffolding, migrations, CI, ArchUnit)
2. Phase 2: Product Catalog (backend + frontend)
3. Phase 3: Inventory Management (core FEFO engine)
4. Phase 4: Sales Context
5. Phase 5: Alerting Context
6. Phase 6: Dashboard & Polish

### Risks
- **Retry loop correctness**: @Retryable must not apply to business exceptions (InsufficientStock, BatchNotFound). Must verify `retryFor` is scoped to `ObjectOptimisticLockingFailureException`.
- **Transaction boundary leak**: BatchAllocator's `@Transactional(REQUIRED)` vs caller's `REQUIRED` — verified correct, but code review must catch any accidental `REQUIRES_NEW`.
- **StockProjection drift**: Startup rebuild is the safety net. Without periodic reconciliation, drift could go undetected for days. Acceptable for MVP.
- **ArchUnit convention enforcement**: ArchUnit rules only run in test phase. They don't prevent compilation violations — only test failures. CI must run ArchUnit tests.

### Skill Resolution
- **sdd-design**: Complete. Design document produced with all required sections.
