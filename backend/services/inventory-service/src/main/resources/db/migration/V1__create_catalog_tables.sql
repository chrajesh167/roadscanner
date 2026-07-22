-- Inventory Service schema — the catalog/metadata tables this service owns outright.
-- See docs/services/inventory-service/domain-model.md for the conceptual model.
--
-- This is the only service that ever writes to these tables — no other RoadScanner service is
-- granted access to this database (docs/architecture/database-ownership.md).

CREATE TABLE cities
(
    id      UUID PRIMARY KEY,
    name    VARCHAR(255) NOT NULL,
    state   VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL
);

-- The hot path for city autocomplete (BrowseCities).
CREATE INDEX idx_cities_name_prefix ON cities (name text_pattern_ops);

CREATE TABLE stations
(
    id        UUID PRIMARY KEY,
    city_id   UUID         NOT NULL,
    name      VARCHAR(255) NOT NULL,
    type      VARCHAR(20)  NOT NULL,
    latitude  DOUBLE PRECISION,
    longitude DOUBLE PRECISION,

    CONSTRAINT fk_stations_city FOREIGN KEY (city_id) REFERENCES cities (id),
    CONSTRAINT chk_stations_type CHECK (type IN ('BUS_STAND', 'TERMINAL'))
);

CREATE INDEX idx_stations_name_prefix ON stations (name text_pattern_ops);
CREATE INDEX idx_stations_city_id ON stations (city_id);

CREATE TABLE routes
(
    id                  UUID PRIMARY KEY,
    origin_city_id      UUID NOT NULL,
    destination_city_id UUID NOT NULL,
    distance_km         DOUBLE PRECISION,

    CONSTRAINT fk_routes_origin_city FOREIGN KEY (origin_city_id) REFERENCES cities (id),
    CONSTRAINT fk_routes_destination_city FOREIGN KEY (destination_city_id) REFERENCES cities (id),
    CONSTRAINT uq_routes_cities UNIQUE (origin_city_id, destination_city_id),
    CONSTRAINT chk_routes_distinct_cities CHECK (origin_city_id != destination_city_id)
);

-- Backs SynchronizeProviderCatalogService's per-route provider search.
CREATE INDEX idx_routes_origin_destination ON routes (origin_city_id, destination_city_id);

CREATE TABLE operator_refs
(
    operator_id   UUID PRIMARY KEY,
    display_name  VARCHAR(255) NOT NULL
);

-- No foreign keys from trips to cities/stations/operator_refs by design: trips.route_id is a
-- best-effort reference (may be null — see boundaries.md, first-party ingestion doesn't resolve
-- a route), and operator_id references operator-service's own id space, not a local table this
-- service controls the write side of in the strict referential-integrity sense.
CREATE TABLE trips
(
    id                    UUID PRIMARY KEY,
    route_id              UUID,
    origin                VARCHAR(255)   NOT NULL,
    destination           VARCHAR(255)   NOT NULL,
    departure_time        TIMESTAMPTZ    NOT NULL,
    arrival_time          TIMESTAMPTZ    NOT NULL,
    operator_id           UUID,
    operator_display_name VARCHAR(255)   NOT NULL,
    bus_id                UUID,
    bus_type_category     VARCHAR(100)   NOT NULL,
    amenities             TEXT,
    fare_amount           NUMERIC(12, 2) NOT NULL,
    fare_currency         VARCHAR(3)     NOT NULL,
    fare_captured_at      TIMESTAMPTZ    NOT NULL,
    bookable              BOOLEAN        NOT NULL DEFAULT TRUE,
    supply_origin         VARCHAR(20)    NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL,
    last_event_at         TIMESTAMPTZ    NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT chk_trips_arrival_after_departure CHECK (arrival_time > departure_time),
    CONSTRAINT chk_trips_fare_non_negative CHECK (fare_amount >= 0),
    CONSTRAINT chk_trips_supply_origin CHECK (supply_origin IN ('FIRST_PARTY', 'PROVIDER_SYNCED'))
);

CREATE INDEX idx_trips_operator_id ON trips (operator_id);
CREATE INDEX idx_trips_route_id ON trips (route_id);

CREATE TABLE seat_layouts
(
    trip_id UUID PRIMARY KEY,

    CONSTRAINT fk_seat_layouts_trip FOREIGN KEY (trip_id) REFERENCES trips (id)
);

-- No status column exists here, or ever should — see Seat's Javadoc. This table holds shape only.
CREATE TABLE seat_layout_seats
(
    trip_id               UUID         NOT NULL,
    seat_number            VARCHAR(20)  NOT NULL,
    deck                    VARCHAR(20)  NOT NULL,
    seat_type               VARCHAR(50)  NOT NULL,
    wheelchair_accessible   BOOLEAN      NOT NULL DEFAULT FALSE,
    position                INTEGER,

    CONSTRAINT fk_seat_layout_seats_trip FOREIGN KEY (trip_id) REFERENCES seat_layouts (trip_id)
);

CREATE INDEX idx_seat_layout_seats_trip_id ON seat_layout_seats (trip_id);

CREATE TABLE provider_mappings
(
    trip_id          UUID PRIMARY KEY,
    provider_type    VARCHAR(50) NOT NULL,
    provider_trip_id VARCHAR(255) NOT NULL,
    last_synced_at   TIMESTAMPTZ NOT NULL,
    sync_status      VARCHAR(20) NOT NULL,

    CONSTRAINT fk_provider_mappings_trip FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT uq_provider_mappings_provider_trip UNIQUE (provider_type, provider_trip_id),
    CONSTRAINT chk_provider_mappings_sync_status CHECK (sync_status IN ('SUCCESS', 'FAILED', 'IN_PROGRESS'))
);

-- The hot path for GetTripAvailability's facade and catalog-sync reconciliation.
CREATE INDEX idx_provider_mappings_provider_trip ON provider_mappings (provider_type, provider_trip_id);

CREATE TABLE sync_records
(
    id               UUID PRIMARY KEY,
    provider_type    VARCHAR(50) NOT NULL,
    last_attempt_at  TIMESTAMPTZ NOT NULL,
    status           VARCHAR(20) NOT NULL,
    catalog_version  BIGINT      NOT NULL DEFAULT 0,
    error_detail     TEXT,

    CONSTRAINT chk_sync_records_status CHECK (status IN ('SUCCESS', 'FAILED', 'IN_PROGRESS'))
);

-- Backs "latest sync record per provider" (GetSyncStatus) — see SyncRecordRepositoryAdapter.
CREATE INDEX idx_sync_records_provider_type_attempt ON sync_records (provider_type, last_attempt_at DESC);
