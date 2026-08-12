-- The notification log: what was sent, to whom, on which channel, and how it went.
--
-- Its reason for existing is the unique constraint below, not the audit trail. Kafka delivers at
-- least once, so a redelivered BookingConfirmed reaches this service as a byte-identical message;
-- without a uniqueness rule enforced by the database, two consumer instances would both see no
-- prior record and both send. A read-then-write check in Java cannot close that window.

CREATE TABLE notification_log
(
    id             UUID         NOT NULL,
    event_id       UUID         NOT NULL,
    booking_id     UUID         NOT NULL,
    event_type     VARCHAR(40)  NOT NULL,
    channel        VARCHAR(20)  NOT NULL,
    -- Stored in full: answering "where did this actually go" after a customer reports receiving
    -- nothing is the whole point of the log. It is only ever *rendered* masked — see Recipient.
    recipient      VARCHAR(320) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL,
    sent_at        TIMESTAMPTZ,

    CONSTRAINT notification_log_pkey PRIMARY KEY (id),

    -- The idempotency identity. Per channel, not per event: a booking whose traveler wants both an
    -- email and an SMS legitimately produces two rows for one event, and neither may repeat.
    CONSTRAINT uq_notification_log_event_channel UNIQUE (event_id, channel),

    CONSTRAINT chk_notification_log_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT chk_notification_log_event_type CHECK (
        event_type IN ('BOOKING_CONFIRMED', 'BOOKING_CANCELLED', 'PAYMENT_FAILED')),
    -- DEMO_RECORDED is a first-class outcome, not a variant of SENT: a stand-in SMS adapter must
    -- never leave a row claiming a carrier accepted a message that was never handed to one.
    CONSTRAINT chk_notification_log_status CHECK (
        status IN ('PENDING', 'SENT', 'DEMO_RECORDED', 'FAILED'))
);

-- Supports "what happened to this booking's notifications", the question support actually asks.
CREATE INDEX idx_notification_log_booking_id ON notification_log (booking_id, created_at DESC);

COMMENT ON COLUMN notification_log.status IS
    'PENDING claimed before sending; SENT accepted by a real provider; DEMO_RECORDED recorded by a '
        'stand-in adapter with no carrier behind it; FAILED rejected, with failure_reason set.';
