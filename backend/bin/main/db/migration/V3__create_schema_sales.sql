-- V3__create_schema_sales.sql
-- Sales bounded context schema.
-- Owned by: SalesContext

CREATE SCHEMA IF NOT EXISTS sales;
SET search_path TO sales;

-- Sales — aggregate root
CREATE TABLE sales (
    id              UUID         PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    sale_number     VARCHAR(50)  NOT NULL,
    customer_name   VARCHAR(200),
    customer_id     VARCHAR(36),
    sale_date       TIMESTAMP    NOT NULL DEFAULT NOW(),
    sale_type       VARCHAR(20)  NOT NULL DEFAULT 'MANUAL_ENTRY',
    subtotal        DECIMAL(12, 2),
    total_amount    DECIMAL(12, 2) NOT NULL,
    performed_by    VARCHAR(100),
    status          VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED'
                    CHECK (status IN ('COMPLETED', 'VOIDED')),
    voided_at       TIMESTAMP,
    void_reason     VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_sales_tenant ON sales(tenant_id);
CREATE INDEX idx_sales_created ON sales(tenant_id, created_at DESC);

-- Sale line items
CREATE TABLE sale_line_items (
    id              UUID         PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    sale_id         UUID         NOT NULL REFERENCES sales(id),
    product_id      UUID         NOT NULL,
    product_name    VARCHAR(200) NOT NULL,
    quantity        INTEGER      NOT NULL CHECK (quantity > 0),
    unit_price      DECIMAL(12, 2) NOT NULL,
    line_total      DECIMAL(12, 2) NOT NULL,
    batch_id        VARCHAR(36),
    batch_code      VARCHAR(100),
    cost_per_unit   DECIMAL(12, 4),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sale_items_sale ON sale_line_items(sale_id);
CREATE INDEX idx_sale_items_product ON sale_line_items(tenant_id, product_id);
