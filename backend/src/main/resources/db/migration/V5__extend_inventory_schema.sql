-- V5__extend_inventory_schema.sql
-- Extends the inventory schema with additional columns and types needed
-- by the Inventory Core bounded context.

SET search_path TO inventory;

-- Add missing columns to batches
ALTER TABLE batches ADD COLUMN IF NOT EXISTS category VARCHAR(100);

-- Extend movement types for full Inventory Core support
ALTER TABLE movements DROP CONSTRAINT IF EXISTS movements_type_check;
ALTER TABLE movements ADD CONSTRAINT movements_type_check
    CHECK (type IN ('RECEIPT', 'SALE', 'ADJUSTMENT', 'RETURN', 'WRITE_OFF', 'TRANSFER', 'ALLOCATED', 'QUARANTINE_IN', 'QUARANTINE_OUT'));

-- Add performed_by column to movements (who performed the action)
ALTER TABLE movements ADD COLUMN IF NOT EXISTS performed_by VARCHAR(100);

-- Add previous_quantity and new_quantity columns for audit trail
ALTER TABLE movements ADD COLUMN IF NOT EXISTS previous_quantity INTEGER;
ALTER TABLE movements ADD COLUMN IF NOT EXISTS new_quantity INTEGER;

-- Add notes column to movements
ALTER TABLE movements DROP COLUMN IF EXISTS notes;
ALTER TABLE movements ADD COLUMN IF NOT EXISTS notes TEXT;

-- FEFO composite index for allocation queries
CREATE INDEX IF NOT EXISTS idx_batches_fefo_allocate
    ON batches(product_id, status, expiration_date, current_quantity)
    WHERE status = 'ACTIVE' AND current_quantity > 0;
