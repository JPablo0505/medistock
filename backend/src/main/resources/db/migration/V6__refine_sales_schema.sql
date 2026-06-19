-- V6__refine_sales_schema.sql
-- Refine Sales schema: add batch_allocations table and refine sales/sale_items.
-- V3 created sales and sale_line_items; V6 aligns with the final spec.

CREATE SCHEMA IF NOT EXISTS sales;
SET search_path TO sales;

-- batch_allocations: separate table for one-to-many allocations per item
CREATE TABLE batch_allocations (
    allocation_id      UUID PRIMARY KEY,
    item_id            UUID NOT NULL REFERENCES sale_line_items(id) ON DELETE CASCADE,
    batch_id           UUID NOT NULL,
    batch_code         VARCHAR(100) NOT NULL,
    expiration_date    DATE,
    allocated_quantity INTEGER NOT NULL CHECK (allocated_quantity > 0),
    cost_per_unit      DECIMAL(12, 2) NOT NULL CHECK (cost_per_unit >= 0)
);

CREATE INDEX idx_batch_allocations_item ON batch_allocations(item_id);
CREATE INDEX idx_batch_allocations_batch ON batch_allocations(batch_id);

-- Add product_sku to sale_line_items for snapshot
ALTER TABLE sale_line_items ADD COLUMN IF NOT EXISTS product_sku VARCHAR(50);
ALTER TABLE sale_line_items ADD COLUMN IF NOT EXISTS performed_by VARCHAR(100);
ALTER TABLE sale_line_items ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Add performed_by to sales for audit
ALTER TABLE sales ADD COLUMN IF NOT EXISTS performed_by VARCHAR(100);
ALTER TABLE sales ADD COLUMN IF NOT EXISTS sale_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL_ENTRY';
ALTER TABLE sales ADD COLUMN IF NOT EXISTS subtotal DECIMAL(12, 2);
UPDATE sales SET subtotal = total_amount WHERE subtotal IS NULL;
ALTER TABLE sales ALTER COLUMN subtotal SET NOT NULL;
