-- Payment Service schema — the payment-lifecycle tables this service owns outright.
-- See docs/services/payment-service/domain-model.md and data-ownership.md for the conceptual model.
--
-- This is the only service that ever writes to these tables — no other RoadScanner service is
-- granted access to this database (docs/architecture/database-ownership.md).
--
-- NFR-12: no card / bank / instrument data is stored here — only gateway references and status.

CREATE TABLE payments
(
    id                  UUID PRIMARY KEY,
    booking_reference   UUID           NOT NULL,
    traveler_id         UUID           NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    method              VARCHAR(20)    NOT NULL,
    gateway_type        VARCHAR(50)    NOT NULL,
    gateway_payment_id  VARCHAR(255),
    gateway_order_id    VARCHAR(255),
    gateway_refund_id   VARCHAR(255),
    status              VARCHAR(20)    NOT NULL,
    idempotency_key     VARCHAR(255)   NOT NULL,
    expires_at          TIMESTAMPTZ    NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL,
    authorized_at       TIMESTAMPTZ,
    captured_at         TIMESTAMPTZ,
    failed_at           TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    expired_at          TIMESTAMPTZ,
    version             BIGINT         NOT NULL,

    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key)
);

-- Idempotency / correlation lookups.
CREATE INDEX idx_payments_booking_reference ON payments (booking_reference);
CREATE UNIQUE INDEX uq_payments_gateway_payment_id ON payments (gateway_payment_id)
    WHERE gateway_payment_id IS NOT NULL;
-- Backs Sweep Expired Payments.
CREATE INDEX idx_payments_status_expires_at ON payments (status, expires_at);

-- "At most one non-terminal payment per booking" (FR-4.3) — the database is the final arbiter when
-- two concurrent Initiate Payment calls race. Terminal = FAILED/CANCELLED/EXPIRED/REFUNDED.
CREATE UNIQUE INDEX uq_payments_active_per_booking ON payments (booking_reference)
    WHERE status NOT IN ('FAILED', 'CANCELLED', 'EXPIRED', 'REFUNDED');

CREATE TABLE payment_attempts
(
    payment_id          UUID         NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    attempt_index       INTEGER      NOT NULL,
    attempt_id          UUID         NOT NULL,
    attempt_number      INTEGER      NOT NULL,
    gateway_payment_id  VARCHAR(255),
    gateway_order_id    VARCHAR(255),
    gateway_refund_id   VARCHAR(255),
    outcome             VARCHAR(20)  NOT NULL,
    failure_code        VARCHAR(100),
    failure_reason      VARCHAR(500),
    started_at          TIMESTAMPTZ  NOT NULL,
    settled_at          TIMESTAMPTZ,

    PRIMARY KEY (payment_id, attempt_index)
);

CREATE TABLE refunds
(
    id                  UUID PRIMARY KEY,
    payment_id          UUID           NOT NULL REFERENCES payments (id),
    booking_reference   UUID           NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    full_refund         BOOLEAN        NOT NULL,
    reason              VARCHAR(40)    NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    idempotency_key     VARCHAR(255)   NOT NULL,
    gateway_refund_id   VARCHAR(255),
    created_at          TIMESTAMPTZ    NOT NULL,
    completed_at        TIMESTAMPTZ,
    failed_at           TIMESTAMPTZ,
    version             BIGINT         NOT NULL,

    CONSTRAINT uq_refunds_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_refunds_payment_id ON refunds (payment_id);
CREATE UNIQUE INDEX uq_refunds_gateway_refund_id ON refunds (gateway_refund_id)
    WHERE gateway_refund_id IS NOT NULL;

CREATE TABLE refund_attempts
(
    refund_id           UUID         NOT NULL REFERENCES refunds (id) ON DELETE CASCADE,
    attempt_index       INTEGER      NOT NULL,
    attempt_id          UUID         NOT NULL,
    attempt_number      INTEGER      NOT NULL,
    gateway_refund_id   VARCHAR(255),
    outcome             VARCHAR(20)  NOT NULL,
    failure_code        VARCHAR(100),
    failure_reason      VARCHAR(500),
    started_at          TIMESTAMPTZ  NOT NULL,
    settled_at          TIMESTAMPTZ,

    PRIMARY KEY (refund_id, attempt_index)
);

-- Insert-only internal transaction ledger (docs/architecture/service-boundaries.md).
CREATE TABLE payment_transactions
(
    id                  UUID PRIMARY KEY,
    payment_id          UUID           NOT NULL,
    refund_id           UUID,
    type                VARCHAR(20)    NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    gateway_payment_id  VARCHAR(255),
    gateway_refund_id   VARCHAR(255),
    occurred_at         TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_payment_transactions_payment_id ON payment_transactions (payment_id);

-- Insert-only webhook idempotency / replay-protection store.
CREATE TABLE webhook_events
(
    id                  UUID PRIMARY KEY,
    gateway_type        VARCHAR(50)  NOT NULL,
    gateway_event_id    VARCHAR(255) NOT NULL,
    signature_verified  BOOLEAN      NOT NULL,
    payload_digest      VARCHAR(128),
    processing_outcome  VARCHAR(30)  NOT NULL,
    received_at         TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_webhook_events_gateway_event UNIQUE (gateway_type, gateway_event_id)
);

-- Insert-only security audit trail (FR-8.3, NFR-13).
CREATE TABLE payment_audit_records
(
    id                  UUID PRIMARY KEY,
    event_type          VARCHAR(60)  NOT NULL,
    subject_reference   VARCHAR(255),
    detail              VARCHAR(1024),
    occurred_at         TIMESTAMPTZ  NOT NULL
);

-- Insert-only reconciliation-discrepancy records (surfaced to support, never move money).
CREATE TABLE reconciliation_records
(
    id                  UUID PRIMARY KEY,
    kind                VARCHAR(60)  NOT NULL,
    subject_reference   VARCHAR(255),
    detail              VARCHAR(1024),
    detected_at         TIMESTAMPTZ  NOT NULL
);
