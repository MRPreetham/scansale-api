-- V2: index tenant-scoped reads (every tenant query filters org_id first)

CREATE INDEX idx_memberships_org_created ON memberships (org_id, created_at);
CREATE INDEX idx_movements_org_created ON stock_movements (org_id, created_at);
CREATE INDEX idx_imports_org_imported ON stock_imports (org_id, imported_at);
CREATE INDEX idx_sales_org_sold ON sales (org_id, sold_at);
