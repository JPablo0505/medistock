-- V1__create_schema_product_catalog.sql
-- Product Catalog bounded context schema.
-- Owned by: ProductCatalogContext

CREATE SCHEMA IF NOT EXISTS product_catalog;
SET search_path TO product_catalog;

-- Product categories (enum-like, managed via data seeding)
CREATE TABLE product_categories (
    id          UUID         PRIMARY KEY,
    tenant_id   VARCHAR(36)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_product_categories_tenant ON product_categories(tenant_id);

-- Products — core aggregate
CREATE TABLE products (
    id              UUID         PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    generic_name    VARCHAR(200),
    category_id     UUID         NOT NULL REFERENCES product_categories(id),
    concentration   VARCHAR(100),
    dosage_form     VARCHAR(50),
    manufacturer    VARCHAR(200),
    requires_prescription BOOLEAN NOT NULL DEFAULT FALSE,
    is_controlled   BOOLEAN NOT NULL DEFAULT FALSE,
    storage_condition VARCHAR(50),
    unit            VARCHAR(20)  NOT NULL,
    sale_price      DECIMAL(12, 2) NOT NULL DEFAULT 0,
    purchase_price  DECIMAL(12, 2) NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_products_tenant ON products(tenant_id);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active ON products(tenant_id, is_active);
