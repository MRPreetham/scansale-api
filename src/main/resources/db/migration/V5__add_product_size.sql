-- V5: product pack size (numeric value, interpreted with the unit, e.g. 750 + ml, 1 + ltr)

ALTER TABLE products ADD COLUMN size numeric(15,3);
