-- Durable audit trail — the Postgres side of every ProviderUnavailable/ProviderRecovered/
-- SessionExpired event this service raises (the Kafka side is the same event, published by
-- AuditPublisher). Insert-only: no update/delete path exists for this table by design.

CREATE TABLE audit_records
(
    id            UUID PRIMARY KEY,
    provider_type VARCHAR(50) NOT NULL,
    event_type    VARCHAR(30) NOT NULL,
    session_id    UUID,
    message       TEXT        NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_audit_records_event_type CHECK (event_type IN ('PROVIDER_UNAVAILABLE', 'PROVIDER_RECOVERED', 'SESSION_EXPIRED'))
);

-- The natural query shape for anyone auditing this table directly (per-provider event history,
-- most recent first) — see AuditRecord's Javadoc on why this is queried directly, not via API.
CREATE INDEX idx_audit_records_provider_type_occurred_at ON audit_records (provider_type, occurred_at DESC);
