-- V7: snapshot unit + pack size on sale lines (invoices stay accurate if product changes later)

ALTER TABLE sale_lines ADD COLUMN unit character varying(20) NOT NULL DEFAULT 'pcs';
ALTER TABLE sale_lines ADD COLUMN size numeric(15,3);
