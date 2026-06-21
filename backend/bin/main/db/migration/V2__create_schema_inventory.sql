-- V2__create_schema_inventory.sql
-- Inventory Management bounded context schema.
-- Owned by: InventoryContext

CREATE SCHEMA IF NOT EXISTS inventory;
SET search_path TO inventory;

-- Batches — core aggregate for FEFO engine
CREATE TABLE batches (
    id                UUID         PRIMARY KEY,
    tenant_id         VARCHAR(36)  NOT NULL,
    product_id        VARCHAR(36)  NOT NULL,
    batch_code        VARCHAR(100) NOT NULL,
    initial_quantity  INTEGER      NOT NULL CHECK (initial_quantity > 0),
    current_quantity  INTEGER      NOT NULL CHECK (current_quantity >= 0),
    expiration_date   DATE         NOT NULL,
    manufacturing_date DATE,
    cost_per_unit     DECIMAL(12, 4) NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE', 'EXPIRED', 'QUARANTINED', 'DEPLETED', 'DISPOSED')),
    notes             TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_batches_tenant_product ON batches(tenant_id, product_id);
CREATE INDEX idx_batches_fefo ON batches(tenant_id, product_id, expiration_date, status);
CREATE INDEX idx_batches_status ON batches(tenant_id, status);

-- Movements — immutable append-only ledger
CREATE TABLE movements (
    id              UUID         PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    batch_id        VARCHAR(36)  NOT NULL,
    product_id      VARCHAR(36)  NOT NULL,
    type            VARCHAR(30)  NOT NULL
                    CHECK (type IN ('RECEIPT', 'SALE', 'ADJUSTMENT', 'RETURN', 'WRITE_OFF', 'TRANSFER')),
    quantity        INTEGER      NOT NULL,
    reference_id    VARCHAR(36),          -- sale ID, adjustment ID, etc.
    reason          VARCHAR(200),
    unit_cost       DECIMAL(12, 4),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_movements_batch ON movements(batch_id);
CREATE INDEX idx_movements_product ON movements(tenant_id, product_id);
CREATE INDEX idx_movements_created ON movements(tenant_id, created_at DESC);

-- Stock projection — event-updated read model
CREATE TABLE stock_projection (
    id              UUID         PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    product_id      VARCHAR(36)  NOT NULL,
    total_physical  INTEGER      NOT NULL DEFAULT 0,
    total_available INTEGER      NOT NULL DEFAULT 0,
    near_expiry_qty INTEGER      NOT NULL DEFAULT 0,
    last_batch_expiration DATE,
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0,

    UNIQUE (tenant_id, product_id)
);

CREATE INDEX idx_stock_projection_tenant ON stock_projection(tenant_id);
