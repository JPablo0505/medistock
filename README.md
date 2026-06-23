<p align="center">
  <img src="docs/logo.png" alt="MediStock" width="400" />
</p>

[![CI](https://github.com/JPablo0505/medistock/actions/workflows/ci.yml/badge.svg)](https://github.com/JPablo0505/medistock/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-16-000000?logo=next.js&logoColor=white)](https://nextjs.org/)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-Private-red)](#)

Sistema de gestion de inventario farmaceutico construido con Spring Boot (backend) y Next.js (frontend). disenado para droguerias y farmacias con soporte multi-tenant, trazabilidad de lotes, sistema de alertas y ventas.

---

## Arquitectura

El proyecto sigue un enfoque de **Domain-Driven Design** con bounded contexts separados:

```
MediStock/
├── backend/                    # Spring Boot 3.4.4 / Java 21
│   └── src/main/java/com/medistock/
│       ├── productcatalog/     # Catalogo de productos y categorias
│       ├── inventory/          # Lotes, movimientos de stock, proyecciones
│       ├── sales/              # Ventas, asignacion de lotes
│       ├── alerts/             # Reglas e instancias de alertas
│       ├── dashboard/          # Agregaciones para el dashboard
│       ├── crosscutting/       # Seguridad, JWT, TenantContext
│       └── shared/             # Value objects, base entities
│
├── frontend/                   # Next.js 16 / React 19 / TypeScript
│   └── src/
│       ├── app/(dashboard)/    # Pages: products, batches, ventas, alertas
│       ├── components/         # UI components (shadcn/ui)
│       ├── hooks/              # React Query hooks
│       ├── lib/                # API client, formatters
│       └── types/              # TypeScript type definitions
│
└── .github/workflows/ci.yml   # GitHub Actions CI/CD
```

---

## Stack Tecnologico

### Backend

| Componente | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.4 |
| Spring Data JPA | Hibernate |
| PostgreSQL | 16 |
| Flyway | 10.20.0 |
| SpringDoc OpenAPI | 2.8.6 |
| Auth0 java-jwt | 4.5.0 |
| Lombok | 1.18.36 |
| JUnit 5 | 5.11.4 |
| Mockito | 5.14.2 |
| ArchUnit | 1.4.0 |

### Frontend

| Componente | Version |
|---|---|
| Next.js | 16.2.9 |
| React | 19.2.4 |
| TypeScript | 5.x |
| Tailwind CSS | 4.x |
| TanStack React Query | 5.101.0 |
| Axios | 1.18.0 |
| shadcn/ui (Radix) | Componentes UI |
| Sonner | 2.0.7 (notificaciones) |
| Lucide React | 1.21.0 (iconos) |

---

## Modelo de Dominio

### Productos (`product_catalog`)

Campos principales: `name`, `genericName`, `concentration`, `dosageForm`, `manufacturer`, `requiresPrescription`, `isControlled`, `storageCondition`, `unit`, `salePrice`, `purchasePrice`, `expirationDate`, `manufacturingDate`, `stockQuantity` (calculado desde lotes).

### Lotes (`inventory`)

Cada producto tiene uno o mas lotes con trazabilidad individual.

- **Estados**: `ACTIVE`, `EXPIRED`, `QUARANTINED`, `DEPLETED`, `DISPOSED`
- **Campos**: `batchCode`, `initialQuantity`, `currentQuantity`, `expirationDate`, `costPerUnit`
- **Movimientos**: `RECEIPT`, `ADJUSTMENT`, `WRITE_OFF`, `TRANSFER`, `ALLOCATED`, `QUARANTINE_IN`, `QUARANTINE_OUT`

### Ventas (`sales`)

Una venta agrupa uno o mas items, cada uno con asignacion de lote (FIFO).

- **Estados**: `COMPLETED`, `VOIDED`
- **Numero de venta**: formato `V-yyyyMMdd-HHmmss-UUID4` (auto-generado)
- **Campos**: `subtotal`, `total`, `status`, `voidedAt`, `voidReason`

### Alertas (`alerting`)

Sistema de reglas configurables que genera instancias de alerta automaticamente.

- **Tipos de regla**: `EXPIRATION` (vencimiento en 30 dias), `LOW_STOCK` (stock = 0)
- **Severidad**: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- **Estados de instancia**: `ACTIVE`, `ACKNOWLEDGED`, `RESOLVED`
- Las alertas se evaluan automaticamente al cargar la pagina

---

## Base de Datos

PostgreSQL con 4 schemas separados, manejados por Flyway:

| Schema | Proposito | Migraciones |
|---|---|---|
| `product_catalog` | Productos, categorias | V1, V8, V9, V10 |
| `inventory` | Lotes, movimientos | V2, V5 |
| `sales` | Ventas, items, allocaciones | V3, V6, V11 |
| `alerting` | Reglas e instancias de alertas | V4, V7 |

Migraciones adicionales:
- **V10**: Asegura `expirationDate` y `manufacturingDate` como NOT NULL
- **V11**: Agrega `sale_number` con auto-generacion
- **V12**: Normaliza todos los `tenant_id` a `'default'`

---

## Endpoints API

### Productos
- `GET /api/v1/products` - Listar productos (filtro por `?active=true/false`)
- `GET /api/v1/products/{id}` - Detalle de producto con stock
- `POST /api/v1/products` - Crear producto
- `PUT /api/v1/products/{id}` - Actualizar producto
- `DELETE /api/v1/products/{id}` - Desactivar producto
- `PATCH /api/v1/products/{id}/reactivate` - Reactivar producto

### Lotes
- `GET /api/v1/batches` - Listar lotes
- `GET /api/v1/batches/{id}` - Detalle de lote con movimientos
- `POST /api/v1/batches` - Recibir stock (crear lote)
- `POST /api/v1/batches/{id}/quarantine` - Poner en cuarentena
- `POST /api/v1/batches/{id}/release` - Liberar de cuarentena
- `POST /api/v1/batches/{id}/dispose` - Desechar lote
- `POST /api/v1/batches/{id}/adjust` - Ajustar cantidad
- `GET /api/v1/batches/stock/{productId}` - Resumen de stock por producto

### Ventas
- `GET /api/v1/sales` - Listar ventas (paginado)
- `GET /api/v1/sales/{id}` - Detalle de venta con items
- `POST /api/v1/sales` - Crear venta (asignacion automatica de lotes)
- `POST /api/v1/sales/{id}/void` - Anular venta

### Alertas
- `GET /api/v1/alerts/instances` - Listar instancias de alerta
- `GET /api/v1/alerts/rules` - Listar reglas de alerta
- `POST /api/v1/alerts/evaluate` - Evaluar reglas manualmente
- `POST /api/v1/alerts/instances/{id}/acknowledge` - Acusar recibo
- `POST /api/v1/alerts/instances/{id}/resolve` - Resolver alerta
- `POST /api/v1/alerts/rules` - Crear regla

### Dashboard
- `GET /api/v1/dashboard/summary` - Resumen: total productos, lotes activos, alertas activas, proximos a vencer

---

## Configuracion

### Variables de entorno

| Variable | Default | Descripcion |
|---|---|---|
| `DB_USERNAME` | `postgres` | Usuario de PostgreSQL |
| `DB_PASSWORD` | `root` | Contrasena de PostgreSQL |

### Servidor

- **Backend**: `http://localhost:8080`
- **Frontend**: `http://localhost:3000`
- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`

---

## Instalacion

### Requisitos

- Java 21+
- Node.js 20+
- PostgreSQL 16+

### Backend

```bash
cd backend
# La base de datos se crea automaticamente via Flyway
.\gradlew.bat bootRun    # Windows
./gradlew bootRun        # Linux/Mac
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

---

## Testing

### Backend

```bash
cd backend
.\gradlew.bat test       # Windows
./gradlew test           # Linux/Mac
```

Tests unitarios con JUnit 5 + Mockito. Tests de integracion con `@WebMvcTest` para controllers. Arquitectura verificada con ArchUnit.

### Frontend

```bash
cd frontend
npm run lint             # ESLint
npx tsc --noEmit         # Type checking
npm run build            # Build de produccion
```

---

## CI/CD

GitHub Actions ejecuta en cada push y PR a `main`, `feature/**`, `feat/**`:

### Backend
- Container PostgreSQL 16-alpine como servicio
- JDK 21 (Temurin) con cache de Gradle
- `./gradlew build` (compila + tests)
- Sube test results como artifact

### Frontend
- Node.js 20 con cache de npm
- `npm ci` -> `tsc --noEmit` -> `npm run lint` -> `npm run build`

---

## Principios de Diseno

- **Multi-tenant**: Todos los queries filtran por `tenantId`. Actualmente defaults a `"default"` (sin auth implementada).
- **Hexagonal / DDD**: Controllers -> Use Cases -> Domain -> Ports (repositories). Cada bounded context es un package independiente.
- **Sin estado en el servidor**: Frontend es 100% client-side con React Query para data fetching y cache.
- **Optimistic UI**: Loading skeletons, retry buttons, toast notifications para feedback inmediato.
- **Precios en COP**: Todos los valores monetarios se muestran formateados como pesos colombianos.
- **Flyway**: Migraciones versionadas y automaticas. El schema se valida contra JPA en cada arranque.

---

## Licencia

Proyecto privado. Todos los derechos reservados.
