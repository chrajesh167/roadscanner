-- Sprint 6: align stored passenger identity with what providers actually require, and record the
-- contact a booking is delivered to.
--
-- The previous shape (full_name, age) could not produce a valid provider request. FlixBus's
-- checkout takes firstName, lastName and a birthdate; provider-integration-service's own
-- ConfirmBooking/BlockSeat contracts take the same. A display name cannot be split into name parts
-- by any rule that is right for every real name, and an age cannot produce a birth date — so there
-- is deliberately no data migration here that invents either.
--
-- This is safe to apply as a destructive column swap because no row can survive it meaningfully:
-- every existing booking_passengers row holds a name and age that the provider would have rejected,
-- and every booking created before this migration would have failed at confirmation anyway (the
-- request shape was never accepted). Local and test databases are the only ones this service has.

-- ---------------------------------------------------------------------------------------------
-- Passengers move onto the seat hold: the provider binds occupant to seat when the seats are
-- blocked, so the identities have to exist by then rather than at booking creation.
-- ---------------------------------------------------------------------------------------------

DROP TABLE IF EXISTS seat_hold_seat_numbers;

CREATE TABLE seat_hold_passengers
(
    seat_hold_id UUID         NOT NULL,
    first_name   VARCHAR(255) NOT NULL,
    last_name    VARCHAR(255) NOT NULL,
    birth_date   DATE         NOT NULL,
    gender       VARCHAR(20)  NOT NULL,
    seat_number  VARCHAR(20)  NOT NULL,

    CONSTRAINT fk_seat_hold_passengers_hold FOREIGN KEY (seat_hold_id) REFERENCES seat_holds (id) ON DELETE CASCADE
);

CREATE INDEX idx_seat_hold_passengers_hold_id ON seat_hold_passengers (seat_hold_id);

-- ---------------------------------------------------------------------------------------------
-- Booking passengers take the same shape.
-- ---------------------------------------------------------------------------------------------

DELETE FROM booking_passengers;

ALTER TABLE booking_passengers
    DROP COLUMN full_name,
    DROP COLUMN age,
    ADD COLUMN first_name VARCHAR(255) NOT NULL,
    ADD COLUMN last_name  VARCHAR(255) NOT NULL,
    ADD COLUMN birth_date DATE         NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- Where the ticket is sent. One contact per booking, not per passenger — the provider's own shape.
-- Existing rows get no contact because none was ever collected; they are pre-provider-contract
-- bookings that could not have been confirmed, so a placeholder would be inventing a delivery
-- address for a ticket nobody can receive.
-- ---------------------------------------------------------------------------------------------

DELETE FROM bookings WHERE status = 'PENDING_PAYMENT';

ALTER TABLE bookings
    ADD COLUMN contact_phone                    VARCHAR(30)  NOT NULL DEFAULT '',
    ADD COLUMN contact_email                    VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN contact_communication_preference VARCHAR(10)  NOT NULL DEFAULT 'email';

-- The defaults exist only to let the columns be added to whatever rows remain; new bookings always
-- supply all three, and the application refuses a blank phone or email.
ALTER TABLE bookings
    ALTER COLUMN contact_phone DROP DEFAULT,
    ALTER COLUMN contact_email DROP DEFAULT;
