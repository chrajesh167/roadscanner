-- Sprint 1: the canonical location catalogue.
--
-- `location` is RoadScanner's master record for a place. Every other service — and every
-- client — refers to a place by this table's UUID and nothing else. `provider_location_mapping`
-- is the only place in the platform where a provider's own identifiers are allowed to appear,
-- which is what keeps provider IDs from leaking into public contracts.
--
-- Unlike V1's `searchable_trips`, nothing here is a disposable projection: this is
-- authored/curated master data, so it is never truncated by a rebuild.
--
-- Every instant is TIMESTAMPTZ, matching V1 and the rest of the platform. A plain TIMESTAMP
-- would drop the offset and silently reinterpret stored instants against whatever the session
-- timezone happens to be — the domain models these as `Instant`, which is a point on the
-- timeline, not a wall-clock reading.

CREATE TABLE location
(
    id              UUID PRIMARY KEY,
    display_name    VARCHAR(255)   NOT NULL,
    city            VARCHAR(120)   NOT NULL,
    state           VARCHAR(120),
    country         VARCHAR(120)   NOT NULL,
    latitude        DECIMAL(10, 7),
    longitude       DECIMAL(10, 7),
    -- Nullable now and populated by Sprint 2's Google Places enrichment. The column exists from
    -- the start so that sprint is a data migration, not a schema change.
    google_place_id VARCHAR(255),
    timezone        VARCHAR(80),
    -- Soft delete only. A location referenced by historical trips or provider mappings must
    -- remain resolvable forever, so `DELETE /locations/{id}` clears this flag instead.
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,

    CONSTRAINT chk_location_latitude_range CHECK (latitude IS NULL OR (latitude BETWEEN -90 AND 90)),
    CONSTRAINT chk_location_longitude_range CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180)),
    -- Coordinates are meaningful only as a pair; half a point is not a location.
    CONSTRAINT chk_location_coordinates_paired
        CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL))
);

-- Backs the autocomplete prefix scan.
CREATE INDEX idx_location_display_name ON location (display_name);

CREATE INDEX idx_location_city ON location (city);

-- Postgres treats NULLs as distinct, so this enforces "unique when present" — exactly the
-- optional-but-unique rule google_place_id needs, with no partial-index trickery.
CREATE UNIQUE INDEX idx_location_google_place ON location (google_place_id);


CREATE TABLE provider_location_mapping
(
    id                    UUID PRIMARY KEY,
    -- The provider's own vocabulary lives here and nowhere else in the platform.
    provider              VARCHAR(50)  NOT NULL,
    location_id           UUID         NOT NULL,
    provider_city_id      VARCHAR(255),
    provider_station_id   VARCHAR(255),
    provider_station_name VARCHAR(255),
    -- Deliberately schemaless: each provider carries different extras, and pinning columns now
    -- would force a migration for every provider onboarded later.
    provider_metadata     JSONB,
    -- False until a human or a reconciliation job confirms the mapping is correct. Sprint 3
    -- flips this; Sprint 1 only stores it.
    verified              BOOLEAN      NOT NULL DEFAULT FALSE,
    last_synced           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_provider_location
        FOREIGN KEY (location_id)
            REFERENCES location (id)
);

CREATE INDEX idx_provider_location ON provider_location_mapping (location_id);

CREATE INDEX idx_provider_name ON provider_location_mapping (provider);

-- The two provider-side lookup paths Sprint 3 will use to resolve a provider's own id back to
-- a RoadScanner location.
CREATE INDEX idx_provider_city ON provider_location_mapping (provider, provider_city_id);

CREATE INDEX idx_provider_station ON provider_location_mapping (provider, provider_station_id);
