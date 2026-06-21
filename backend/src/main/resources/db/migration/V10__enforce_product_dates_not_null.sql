-- V10__enforce_product_dates_not_null.sql
-- Makes expiration_date and manufacturing_date NOT NULL on products table.
-- Backfills any existing NULL rows with sensible defaults.

SET search_path TO product_catalog;

-- Backfill NULL expiration_date rows with a far-future default
UPDATE products SET expiration_date = '2026-12-31' WHERE expiration_date IS NULL;

-- Backfill NULL manufacturing_date rows with a past default
UPDATE products SET manufacturing_date = '2025-01-01' WHERE manufacturing_date IS NULL;

-- Add NOT NULL constraints
ALTER TABLE products ALTER COLUMN expiration_date SET NOT NULL;
ALTER TABLE products ALTER COLUMN manufacturing_date SET NOT NULL;
