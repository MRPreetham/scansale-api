-- V1: initial schema (mirrors the Hibernate-generated DDL)
-- qty = numeric(15,3), money = numeric(19,2), timestamps = timestamptz(6)

CREATE TABLE users (
    id            uuid NOT NULL,
    active        boolean NOT NULL,
    created_at    timestamp(6) with time zone NOT NULL,
    email         character varying(255) NOT NULL,
    name          character varying(100) NOT NULL,
    password_hash character varying(100) NOT NULL,
    platform_role character varying(20),
    CONSTRAINT users_platform_role_check CHECK ((platform_role)::text = ANY
        ((ARRAY['SUPER_ADMIN'::character varying, 'SUPPORT'::character varying])::text[])),
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE organizations (
    id         uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    currency   character varying(10) NOT NULL,
    name       character varying(255) NOT NULL,
    status     character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT organizations_status_check CHECK (((status)::text = ANY
        (ARRAY[('ACTIVE'::character varying)::text, ('SUSPENDED'::character varying)::text, ('PENDING'::character varying)::text]))),
    CONSTRAINT organizations_pkey PRIMARY KEY (id)
);

CREATE TABLE memberships (
    id         uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    role       character varying(20) NOT NULL,
    status     character varying(20) NOT NULL,
    org_id     uuid NOT NULL,
    user_id    uuid NOT NULL,
    CONSTRAINT memberships_role_check CHECK (((role)::text = ANY
        (ARRAY[('ADMIN'::character varying)::text, ('SALES'::character varying)::text, ('INVENTORY'::character varying)::text]))),
    CONSTRAINT memberships_status_check CHECK (((status)::text = ANY
        (ARRAY[('ACTIVE'::character varying)::text, ('INVITED'::character varying)::text]))),
    CONSTRAINT memberships_pkey PRIMARY KEY (id),
    CONSTRAINT uk_membership_user_org UNIQUE (user_id, org_id),
    CONSTRAINT fk_memberships_org FOREIGN KEY (org_id) REFERENCES organizations (id),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE products (
    id            uuid NOT NULL,
    available_qty numeric(15,3) NOT NULL,
    barcode       character varying(100) NOT NULL,
    created_at    timestamp(6) with time zone NOT NULL,
    name          character varying(200) NOT NULL,
    notes         character varying(500),
    opening_qty   numeric(15,3) NOT NULL,
    reorder_level numeric(15,3) NOT NULL,
    selling_price numeric(19,2) NOT NULL,
    sku           character varying(50),
    unit          character varying(20) NOT NULL,
    updated_at    timestamp(6) with time zone NOT NULL,
    version       bigint NOT NULL,
    org_id        uuid NOT NULL,
    CONSTRAINT products_pkey PRIMARY KEY (id),
    CONSTRAINT uk_product_org_barcode UNIQUE (org_id, barcode),
    CONSTRAINT fk_products_org FOREIGN KEY (org_id) REFERENCES organizations (id)
);

CREATE TABLE audit_log (
    id           uuid NOT NULL,
    action       character varying(60) NOT NULL,
    created_at   timestamp(6) with time zone NOT NULL,
    details_json text,
    entity_id    character varying(60),
    entity_type  character varying(60),
    actor_id     uuid NOT NULL,
    org_id       uuid NOT NULL,
    CONSTRAINT audit_log_pkey PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_org FOREIGN KEY (org_id) REFERENCES organizations (id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);

CREATE INDEX idx_audit_org_created ON audit_log USING btree (org_id, created_at);

CREATE TABLE platform_audit (
    id           uuid NOT NULL,
    action       character varying(60) NOT NULL,
    created_at   timestamp(6) with time zone NOT NULL,
    details_json text,
    entity_id    character varying(60),
    entity_type  character varying(60),
    actor_id     uuid NOT NULL,
    CONSTRAINT platform_audit_pkey PRIMARY KEY (id),
    CONSTRAINT fk_platform_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);

CREATE INDEX idx_platform_audit_created ON platform_audit USING btree (created_at);

CREATE TABLE sales (
    id           uuid NOT NULL,
    notes        character varying(500),
    number_seq   integer NOT NULL,
    payment_mode character varying(20) NOT NULL,
    sale_number  character varying(30) NOT NULL,
    sold_at      timestamp(6) with time zone NOT NULL,
    status       character varying(20) NOT NULL,
    total_amount numeric(19,2) NOT NULL,
    total_qty    numeric(15,3) NOT NULL,
    year         integer NOT NULL,
    cashier_id   uuid NOT NULL,
    org_id       uuid NOT NULL,
    CONSTRAINT sales_payment_mode_check CHECK (((payment_mode)::text = ANY
        (ARRAY[('CASH'::character varying)::text, ('UPI'::character varying)::text, ('CARD'::character varying)::text, ('CREDIT'::character varying)::text]))),
    CONSTRAINT sales_status_check CHECK (((status)::text = ANY
        (ARRAY[('DRAFT'::character varying)::text, ('SUBMITTED'::character varying)::text, ('VOID'::character varying)::text]))),
    CONSTRAINT sales_pkey PRIMARY KEY (id),
    CONSTRAINT uk_sale_org_year_seq UNIQUE (org_id, year, number_seq),
    CONSTRAINT fk_sales_org FOREIGN KEY (org_id) REFERENCES organizations (id),
    CONSTRAINT fk_sales_cashier FOREIGN KEY (cashier_id) REFERENCES users (id)
);

CREATE TABLE sale_lines (
    id         uuid NOT NULL,
    amount     numeric(19,2) NOT NULL,
    barcode    character varying(100) NOT NULL,
    name       character varying(200) NOT NULL,
    qty        numeric(15,3) NOT NULL,
    unit_price numeric(19,2) NOT NULL,
    product_id uuid NOT NULL,
    sale_id    uuid NOT NULL,
    CONSTRAINT sale_lines_pkey PRIMARY KEY (id),
    CONSTRAINT fk_sale_lines_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_sale_lines_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE stock_imports (
    id           uuid NOT NULL,
    filename     character varying(200) NOT NULL,
    imported_at  timestamp(6) with time zone NOT NULL,
    status       character varying(20) NOT NULL,
    summary_json text,
    imported_by  uuid NOT NULL,
    org_id       uuid NOT NULL,
    mode         character varying(20) DEFAULT 'products'::character varying NOT NULL,
    new_count    integer DEFAULT 0 NOT NULL,
    update_count integer DEFAULT 0 NOT NULL,
    skip_count   integer DEFAULT 0 NOT NULL,
    CONSTRAINT stock_imports_status_check CHECK (((status)::text = ANY
        (ARRAY[('PREVIEW'::character varying)::text, ('COMMITTED'::character varying)::text, ('FAILED'::character varying)::text]))),
    CONSTRAINT stock_imports_pkey PRIMARY KEY (id),
    CONSTRAINT fk_stock_imports_org FOREIGN KEY (org_id) REFERENCES organizations (id),
    CONSTRAINT fk_stock_imports_imported_by FOREIGN KEY (imported_by) REFERENCES users (id)
);

CREATE TABLE stock_movements (
    id            uuid NOT NULL,
    created_at    timestamp(6) with time zone NOT NULL,
    qty_delta     numeric(15,3) NOT NULL,
    type          character varying(20) NOT NULL,
    created_by    uuid NOT NULL,
    org_id        uuid NOT NULL,
    product_id    uuid NOT NULL,
    ref_import_id uuid,
    ref_sale_id   uuid,
    CONSTRAINT stock_movements_type_check CHECK (((type)::text = ANY
        (ARRAY[('SALE'::character varying)::text, ('IMPORT'::character varying)::text, ('ADJUSTMENT'::character varying)::text, ('OPENING'::character varying)::text]))),
    CONSTRAINT stock_movements_pkey PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_org FOREIGN KEY (org_id) REFERENCES organizations (id),
    CONSTRAINT fk_stock_movements_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_movements_import FOREIGN KEY (ref_import_id) REFERENCES stock_imports (id),
    CONSTRAINT fk_stock_movements_sale FOREIGN KEY (ref_sale_id) REFERENCES sales (id)
);

CREATE INDEX idx_move_org_product ON stock_movements USING btree (org_id, product_id);
