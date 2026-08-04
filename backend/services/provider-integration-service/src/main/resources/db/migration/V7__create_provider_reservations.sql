-- Provider-side booking state.
--
-- Until now this service held no booking state, because a seat block was one call describable by a
-- single reference. A real provider block is several calls that mint several identifiers, and the
-- correlation between them exists only in the responses that issued them. Nothing re-derives a
-- provider's internal seat id from a seat label, or an order token from an order id, so the values
-- are written down at the moment they are known. Losing them means a booking that cannot be
-- confirmed and, worse, cannot be cancelled.
--
-- Deliberately provider-neutral: no column here names FlixBus or any FlixBus concept. A cart id, a
-- checkout id and an order token are the same *roles* every provider fills with its own vocabulary,
-- so the next provider stores its equivalents in these columns without a migration.

CREATE TABLE provider_reservations (
    id                       UUID         PRIMARY KEY,
    provider_type            VARCHAR(64)  NOT NULL,

    -- The provider's handle for the hold itself (a cart, a lock, a quote id).
    provider_block_reference VARCHAR(255) NOT NULL,

    -- The provider's trip identifier, opaque to this table. For providers whose booking calls need
    -- several ids, this is the composite the provider itself defines, so the parts travel together
    -- and cannot drift apart.
    provider_trip_id         VARCHAR(512) NOT NULL,

    status                   VARCHAR(32)  NOT NULL,
    blocked_at               TIMESTAMPTZ  NOT NULL,
    expires_at               TIMESTAMPTZ  NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- One hold per provider handle. Without this a retried block could create two rows claiming the
    -- same provider-side cart, and the confirm step would pick one arbitrarily.
    CONSTRAINT uq_provider_reservations_block UNIQUE (provider_type, provider_block_reference)
);

-- The per-seat detail of a hold: which traveller-facing seat maps to which provider seat id, and
-- which provider ticket handle it is bound to. A child table rather than a JSON blob because these
-- are looked up and asserted on individually, and a typed column fails loudly when a value is
-- missing instead of yielding a null deep inside a booking call.
CREATE TABLE provider_reservation_seats (
    id                  UUID         PRIMARY KEY,
    reservation_id      UUID         NOT NULL REFERENCES provider_reservations (id) ON DELETE CASCADE,

    -- The label a traveller recognises.
    seat_number         VARCHAR(32)  NOT NULL,

    -- The provider's own id for that seat on that departure.
    provider_seat_id    VARCHAR(255) NOT NULL,

    -- The provider's ticket handle the seat is attached to.
    provider_ticket_id  VARCHAR(255) NOT NULL,

    -- Ordering matters: tickets, seats and passengers are paired positionally during confirmation.
    position            INT          NOT NULL,

    CONSTRAINT uq_provider_reservation_seats UNIQUE (reservation_id, seat_number)
);

CREATE INDEX idx_provider_reservation_seats_reservation ON provider_reservation_seats (reservation_id);

-- The confirmed booking and the handles needed to read or cancel it afterwards.
CREATE TABLE provider_bookings (
    id                          UUID         PRIMARY KEY,
    reservation_id              UUID         NOT NULL REFERENCES provider_reservations (id),
    provider_type               VARCHAR(64)  NOT NULL,
    booking_reference           VARCHAR(255) NOT NULL,

    -- The pre-order handle, where the provider issues one. Kept even after the order exists: if the
    -- order lookup ever fails, this is the only handle support can use to find the transaction.
    provider_checkout_reference VARCHAR(255),

    provider_order_reference    VARCHAR(255) NOT NULL,

    -- Authorises reading and cancelling the order. An order reference without its token cannot be
    -- cancelled, which is why this is stored rather than re-requested.
    provider_order_token        VARCHAR(512),

    total_fare_amount           NUMERIC(12, 2) NOT NULL,
    total_fare_currency         CHAR(3)      NOT NULL,
    confirmed_at                TIMESTAMPTZ  NOT NULL,
    cancelled_at                TIMESTAMPTZ,
    refunded_amount             NUMERIC(12, 2),
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_provider_bookings_order UNIQUE (provider_type, provider_order_reference)
);

CREATE INDEX idx_provider_bookings_reservation ON provider_bookings (reservation_id);
CREATE INDEX idx_provider_bookings_reference ON provider_bookings (booking_reference);
