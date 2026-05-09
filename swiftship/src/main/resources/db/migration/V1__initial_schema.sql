-- ============================================================
-- SwiftShip Database Schema
-- V1__initial_schema.sql
-- ============================================================

-- ── CARRIERS ──────────────────────────────────────────────
CREATE TABLE carriers (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(150)    NOT NULL,
    email               VARCHAR(255)    NOT NULL UNIQUE,
    phone               VARCHAR(30)     NOT NULL,
    vehicle_plate       VARCHAR(20),
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── WAREHOUSES ────────────────────────────────────────────
CREATE TABLE warehouses (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(150)    NOT NULL,
    city                VARCHAR(100)    NOT NULL,
    address             TEXT            NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── SHIPMENTS ─────────────────────────────────────────────
CREATE TABLE shipments (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tracking_number         VARCHAR(30)     NOT NULL UNIQUE,
    customer_name           VARCHAR(200)    NOT NULL,
    customer_email          VARCHAR(255)    NOT NULL,
    origin_address          TEXT            NOT NULL,
    destination_address     TEXT            NOT NULL,
    origin_warehouse_id     UUID            REFERENCES warehouses(id),
    destination_warehouse_id UUID           REFERENCES warehouses(id),
    carrier_id              UUID            REFERENCES carriers(id),
    status                  VARCHAR(30)     NOT NULL DEFAULT 'CREATED',
    weight_kg               NUMERIC(8,3),
    estimated_delivery_date DATE,
    delivered_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_status CHECK (status IN (
        'CREATED','PICKED_UP','IN_TRANSIT',
        'OUT_FOR_DELIVERY','DELIVERED','CANCELLED','RETURNED'
    ))
);

-- ── SHIPMENT STATUS HISTORY ───────────────────────────────
CREATE TABLE shipment_status_history (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id     UUID            NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    from_status     VARCHAR(30),
    to_status       VARCHAR(30)     NOT NULL,
    notes           TEXT,
    changed_by      VARCHAR(150),
    changed_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_to_status CHECK (to_status IN (
        'CREATED','PICKED_UP','IN_TRANSIT',
        'OUT_FOR_DELIVERY','DELIVERED','CANCELLED','RETURNED'
    ))
);

-- ── OUTBOX (Transactional Outbox Pattern) ─────────────────
CREATE TABLE outbox_events (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID            NOT NULL,
    aggregate_type  VARCHAR(50)     NOT NULL DEFAULT 'SHIPMENT',
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    published       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

-- ── INDEXES ───────────────────────────────────────────────
CREATE INDEX idx_shipments_tracking_number   ON shipments(tracking_number);
CREATE INDEX idx_shipments_status            ON shipments(status);
CREATE INDEX idx_shipments_carrier_id        ON shipments(carrier_id);
CREATE INDEX idx_shipments_estimated_date    ON shipments(estimated_delivery_date);
CREATE INDEX idx_status_history_shipment     ON shipment_status_history(shipment_id);
CREATE INDEX idx_outbox_unpublished          ON outbox_events(published) WHERE published = FALSE;

-- ── SEED DATA ─────────────────────────────────────────────
INSERT INTO warehouses (id, name, city, address) VALUES
  ('a1b2c3d4-0001-0001-0001-000000000001', 'Riyadh Central', 'Riyadh', 'King Fahd Road, Riyadh 12271'),
  ('a1b2c3d4-0002-0002-0002-000000000002', 'Jeddah Hub', 'Jeddah', 'Al Corniche Road, Jeddah 23521'),
  ('a1b2c3d4-0003-0003-0003-000000000003', 'Dammam Logistics', 'Dammam', 'Prince Mohammed Bin Fahd Road, Dammam 32241');

INSERT INTO carriers (id, name, email, phone, vehicle_plate) VALUES
  ('b1c2d3e4-0001-0001-0001-000000000001', 'FastExpress', 'carrier1@swiftship.com', '+966500000001', 'KSA-1234'),
  ('b1c2d3e4-0002-0002-0002-000000000002', 'QuickShip', 'carrier2@swiftship.com', '+966500000002', 'KSA-5678'),
  ('b1c2d3e4-0003-0003-0003-000000000003', 'SpeedyDelivery', 'carrier3@swiftship.com', '+966500000003', 'KSA-9012');
