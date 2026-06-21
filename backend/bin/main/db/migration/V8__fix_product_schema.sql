-- V8__fix_product_schema.sql
-- Makes category_id nullable and adds image_url column to products.

ALTER TABLE product_catalog.products ALTER COLUMN category_id DROP NOT NULL;
ALTER TABLE product_catalog.products ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
