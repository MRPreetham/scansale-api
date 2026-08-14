-- V6: org settings (shop details shown on invoices) — 1:1 with organizations

CREATE TABLE org_settings (
    id         uuid NOT NULL,
    org_id     uuid NOT NULL UNIQUE,
    address    text,
    phone      character varying(30),
    email      character varying(255),
    gstin      character varying(20),
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT org_settings_pkey PRIMARY KEY (id),
    CONSTRAINT fk_org_settings_org FOREIGN KEY (org_id) REFERENCES organizations (id)
);
