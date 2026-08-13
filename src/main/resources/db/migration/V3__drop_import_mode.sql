-- V3: single import flow — the mode concept was removed (import now always
-- increments stock for existing barcodes and creates products for new ones).

ALTER TABLE stock_imports DROP COLUMN IF EXISTS mode;
