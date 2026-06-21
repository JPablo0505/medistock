-- Backfill sale_number for existing sales
UPDATE sales.sales
SET sale_number = 'V-' || TO_CHAR(created_at, 'YYYYMMDD-HH24MISS') || '-' || UPPER(LEFT(id::text, 4))
WHERE sale_number IS NULL;

-- Ensure NOT NULL constraint
ALTER TABLE sales.sales ALTER COLUMN sale_number SET NOT NULL;
