-- Booking Service schema — the booking-lifecycle tables this service owns outright.
-- See docs/services/booking-service/domain-model.md for the conceptual model.
--
-- This is the only service that ever writes to these tables — no other RoadScanner service is
-- granted access to this database (docs/architecture/database-ownership.md).

CREATE TABLE seat_holds
(
    id                        UUID PRIMARY KEY,
    traveler_id               UUID           NOT NULL,
    trip_id                   UUID           NOT NULL,
    trip_departure_time       TIMESTAMPTZ    NOT NULL,
    provider_type             VARCHAR(50)    NOT NULL,
    provider_trip_id          VARCHAR(255)   NOT NULL,
    provider_block_reference  VARCHAR(255)   NOT NULL,
    fare_amount               NUMERIC(12, 2) NOT NULL,
    fare_currency              VARCHAR(3)     NOT NULL,
    expires_at                TIMESTAMPTZ    NOT NULL,
    created_at                TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uq_seat_holds_provider_block_reference UNIQUE (provider_block_reference)
);

-- Backs SeatHoldRepositoryAdapter.findByProviderBlockReference (Handle Seat Released) and the
-- ownership check inside Create Booking/Release Hold.
CREATE INDEX idx_seat_holds_traveler_id ON seat_holds (traveler_id);

-- Backs Sweep Stale Holds.
CREATE INDEX idx_seat_holds_expires_at ON seat_holds (expires_at);

CREATE TABLE seat_hold_seat_numbers
(
    seat_hold_id UUID         NOT NULL,
    seat_number  VARCHAR(20)  NOT NULL,

    CONSTRAINT fk_seat_hold_seat_numbers_hold FOREIGN KEY (seat_hold_id) REFERENCES seat_holds (id) ON DELETE CASCADE
);

CREATE INDEX idx_seat_hold_seat_numbers_hold_id ON seat_hold_seat_numbers (seat_hold_id);

CREATE TABLE bookings
(
    id                          UUID PRIMARY KEY,
    traveler_id                 UUID           NOT NULL,
    trip_id                     UUID           NOT NULL,
    trip_departure_time         TIMESTAMPTZ    NOT NULL,
    provider_type               VARCHAR(50)    NOT NULL,
    provider_trip_id            VARCHAR(255)   NOT NULL,
    provider_block_reference    VARCHAR(255)   NOT NULL,
    hold_expires_at             TIMESTAMPTZ    NOT NULL,
    provider_booking_reference  VARCHAR(255),
    fare_amount                 NUMERIC(12, 2) NOT NULL,
    fare_currency                VARCHAR(3)     NOT NULL,
    status                       VARCHAR(20)    NOT NULL,
    cancellation_reason          VARCHAR(30),
    support_flagged              BOOLEAN        NOT NULL DEFAULT FALSE,
    payment_reference            VARCHAR(255),
    ticket_provider_ticket_id    VARCHAR(255),
    ticket_format                 VARCHAR(20),
    ticket_content                 BYTEA,
    ticket_issued_at               TIMESTAMPTZ,
    created_at                     TIMESTAMPTZ    NOT NULL,
    confirmed_at                   TIMESTAMPTZ,
    cancelled_at                    TIMESTAMPTZ,
    completed_at                    TIMESTAMPTZ,
    version                         BIGINT         NOT NULL DEFAULT 0,

    -- A hold token becomes at most one booking (docs/architecture/booking-flow.md's idempotency
    -- requirement) — enforced here as the concrete uniqueness constraint that document leaves as
    -- an implementation decision.
    CONSTRAINT uq_bookings_provider_block_reference UNIQUE (provider_block_reference),
    CONSTRAINT chk_bookings_status CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT chk_bookings_cancellation_reason CHECK (cancellation_reason IN
        ('PAYMENT_FAILED', 'PAYMENT_TIMED_OUT', 'HOLD_EXPIRED', 'TRAVELER_REQUESTED', 'TRIP_CANCELLED',
         'PROVIDER_CONFIRMATION_FAILED'))
);

-- Backs List Booking History (FR-1.3).
CREATE INDEX idx_bookings_traveler_id ON bookings (traveler_id);

-- Backs List Trip Bookings (FR-5.5) and Handle Trip Cancelled's cascade.
CREATE INDEX idx_bookings_trip_id ON bookings (trip_id);
CREATE INDEX idx_bookings_trip_id_status ON bookings (trip_id, status);

-- Backs Complete Booking's scheduled sweep.
CREATE INDEX idx_bookings_status_departure ON bookings (status, trip_departure_time);

-- Backs Sweep Stale Holds' defensive check for a PENDING_PAYMENT booking whose hold expired.
CREATE INDEX idx_bookings_status_hold_expires ON bookings (status, hold_expires_at);

-- Backs Verify Booking (FR-7.2).
CREATE INDEX idx_bookings_traveler_trip_status ON bookings (traveler_id, trip_id, status);

CREATE TABLE booking_passengers
(
    booking_id   UUID         NOT NULL,
    full_name    VARCHAR(255) NOT NULL,
    age          INTEGER      NOT NULL,
    gender       VARCHAR(20)  NOT NULL,
    seat_number  VARCHAR(20)  NOT NULL,

    CONSTRAINT fk_booking_passengers_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE
);

CREATE INDEX idx_booking_passengers_booking_id ON booking_passengers (booking_id);
