## feat(foundation): scaffold Gradle, Next.js, shared kernel, CI

PR-01 Foundation — Feature Branch Chain (1/8).

Backend scaffold with Spring Boot 3.x, shared kernel (AggregateRoot, DomainEvent,
ValueObject, BaseEntity), crosscutting (TenantContext, JwtFilter, CorrelationIdFilter,
GlobalExceptionHandler), Flyway V1-V4 migrations, ArchUnit architectural tests,
GitHub Actions CI. Frontend with Next.js 16, Tailwind CSS 4, shadcn/ui, TanStack Query,
and application shell (sidebar, topbar, providers, api-client, types).

### Review Summary
- **65 files changed, ~10,740 insertions**
- 23/23 shared kernel tests passing
- Backend compiles cleanly, frontend builds successfully
- 9 ArchUnit rules enforceable, 1 convention rule
- **Required fix applied**: Correlation ID now propagates to error responses

### What's included
- Gradle build with Spring Boot 3.4, Java 21, Flyway, ArchUnit, Testcontainers
- Shared domain kernel (marker interfaces + abstract DomainException)
- BaseEntity (UUID PK, tenant_id, created_at, updated_at, @Version)
- JwtFilter (Authorization header to TenantContext ThreadLocal)
- CorrelationIdFilter (X-Correlation-Id header + MDC + request attribute)
- GlobalExceptionHandler (validation, optimistic lock, domain, 404, 403, 500)
- Flyway V1 (product_catalog), V2 (inventory), V3 (sales), V4 (alerting)
- ArchUnit rules enforcing hexagonal architecture boundaries
- GitHub Actions CI (backend with PostgreSQL + frontend lint/type/build)
- Next.js 16 scaffold with shadcn/ui components and layout shell

## Chain Context

| Field | Value |
|-------|-------|
| Chain | medistock-v1 |
| Tracker PR | (tracker branch: feature/medistock-v1) |
| Position | 1 of 8 |
| Base | feature/medistock-v1 |
| Depends on | None |
| Follow-up | PR-02 Product Catalog |
| Review budget | ~10,740 / 400 (chain strategy: feature-branch-chain) |
| Starts at | Initial project setup |
| Ends with | Foundation ready for domain implementation |

### Chain Overview

```text
main
 └── feature/medistock-v1 (tracker)
      └── 📍 feat/medistock-pr01 ← THIS PR (1/8)
           └── feat/medistock-pr02 (next: Product Catalog)
```

### Scope
- **Includes**: Backend scaffold, crosscutting, migrations, ArchUnit, CI, frontend scaffold
- **Excludes**: Any domain logic, business rules, REST endpoints, UI pages

### Autonomy
- [x] CI is expected to pass for this PR branch
- [x] This PR has one deliverable scope
- [x] This PR can be rolled back without unrelated changes
- [x] Tests, docs, or manual verification cover this unit
