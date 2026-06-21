-- V12__normalize_tenant_ids.sql
-- Normalize all tenant_id values to 'default' across all schemas.
-- Data was created with inconsistent tenant_id values ('tenant', 'tenant-1', etc.)
-- because TenantContext was null and controllers didn't default consistently.

-- Product Catalog
UPDATE product_catalog.products SET tenant_id = 'default' WHERE tenant_id != 'default';
UPDATE product_catalog.product_categories SET tenant_id = 'default' WHERE tenant_id != 'default';

-- Inventory
UPDATE inventory.batches SET tenant_id = 'default' WHERE tenant_id != 'default';
UPDATE inventory.movements SET tenant_id = 'default' WHERE tenant_id != 'default';
UPDATE inventory.stock_projection SET tenant_id = 'default' WHERE tenant_id != 'default';

-- Sales
UPDATE sales.sales SET tenant_id = 'default' WHERE tenant_id != 'default';
UPDATE sales.sale_line_items SET tenant_id = 'default' WHERE tenant_id != 'default';

-- Alerting
UPDATE alerting.alert_rules SET tenant_id = 'default' WHERE tenant_id != 'default';
UPDATE alerting.alert_instances SET tenant_id = 'default' WHERE tenant_id != 'default';
