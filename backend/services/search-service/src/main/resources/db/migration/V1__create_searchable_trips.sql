-- Search Service schema — a single table holding the derived read-model projection.
-- See docs/services/search-service/domain-model.md and data-ownership.md.
--
-- No foreign keys: trip_id and operator_id are foreign values owned by operator-service and
-- inventory-service's own databases (docs/architecture/database-ownership.md — "no service
-- reads or writes another service's database, directly or indirectly"). There is nothing local
-- for a foreign key to reference, and there never will be.

CREATE TABLE searchable_trips
(
    trip_id              UUID PRIMARY KEY,
    operator_id          UUID           NOT NULL,
    operator_name        VARCHAR(255)   NOT NULL,
    origin               VARCHAR(255)   NOT NULL,
    destination          VARCHAR(255)   NOT NULL,
    departure_time       TIMESTAMPTZ    NOT NULL,
    arrival_time         TIMESTAMPTZ    NOT NULL,
    -- Generated, not computed in application code, specifically so "sort by duration" can be a
    -- plain indexed column sort at the database layer rather than a derived expression
    -- recomputed per row on every query.
    duration_seconds     INTEGER GENERATED ALWAYS AS (EXTRACT(EPOCH FROM (arrival_time - departure_time))::INTEGER) STORED,
    bus_type_category    VARCHAR(100)   NOT NULL,
    amenities            TEXT,
    fare_amount          NUMERIC(12, 2) NOT NULL,
    fare_currency        VARCHAR(3)     NOT NULL,
    bookable             BOOLEAN        NOT NULL DEFAULT TRUE,
    rating_average       DOUBLE PRECISION NOT NULL DEFAULT 0,
    rating_review_count  INTEGER        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ    NOT NULL,
    last_trip_event_at   TIMESTAMPTZ    NOT NULL,
    last_rating_event_at TIMESTAMPTZ    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT chk_searchable_trips_arrival_after_departure CHECK (arrival_time > departure_time),
    CONSTRAINT chk_searchable_trips_fare_non_negative CHECK (fare_amount >= 0),
    CONSTRAINT chk_searchable_trips_rating_range CHECK (rating_average >= 0 AND rating_average <= 5),
    CONSTRAINT chk_searchable_trips_review_count_non_negative CHECK (rating_review_count >= 0)
);

-- The hot path for every search (FR-2.1) — origin/destination equality plus a departure-time
-- range is the shape of every query this service ever runs against Postgres.
CREATE INDEX idx_searchable_trips_search ON searchable_trips (origin, destination, departure_time);

-- Prefix matching for "Search Suggestions" (docs/services/search-service/use-cases.md).
-- text_pattern_ops lets a plain B-tree serve a `LIKE 'prefix%'` query efficiently without the
-- pg_trgm extension, which this simple prefix-only use case doesn't need.
CREATE INDEX idx_searchable_trips_origin_prefix ON searchable_trips (origin text_pattern_ops);
CREATE INDEX idx_searchable_trips_destination_prefix ON searchable_trips (destination text_pattern_ops);
