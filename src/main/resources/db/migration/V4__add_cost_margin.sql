-- V4: product cost price + manually-entered profit margin (both money columns)

ALTER TABLE products ADD COLUMN cost_price numeric(19,2) NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN profit_margin numeric(19,2) NOT NULL DEFAULT 0;
