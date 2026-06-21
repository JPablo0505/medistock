-- V9__add_product_dates.sql
-- Adds expiration_date and manufacturing_date columns to products.
-- These were previously only on batches; now also available at product level.

ALTER TABLE product_catalog.products ADD COLUMN IF NOT EXISTS expiration_date DATE;
ALTER TABLE product_catalog.products ADD COLUMN IF NOT EXISTS manufacturing_date DATE;
