-- Sprint 2: provider foundation.
--
-- Extends the registry this service already owns rather than introducing a second one. The
-- platform has exactly one answer to "which providers exist and are they enabled", and it lives
-- here (docs/architecture/decisions/sprint-2-provider-foundation.md).
--
-- Note what is deliberately NOT added: per-capability boolean columns. `capabilities` already
-- carries the same facts via ProviderCapability, is already what GetProviderCapabilities reads,
-- and six parallel booleans would be a second copy of that truth, free to drift from it.

-- `provider_type` is already the provider's unique code (FLIXBUS, MOCK). What was missing is the
-- vertical it belongs to — this is the column that lets rail and airline providers land later
-- without reshaping the table.
ALTER TABLE provider_configurations
    ADD COLUMN provider_category VARCHAR(50) NOT NULL DEFAULT 'BUS';

-- Per-provider resilience settings. Previously these were global; a provider whose API is slow
-- should not force every other provider's timeout up to match it.
ALTER TABLE provider_configurations
    ADD COLUMN timeout_ms INTEGER NOT NULL DEFAULT 5000;

ALTER TABLE provider_configurations
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 2;

ALTER TABLE provider_configurations
    ADD CONSTRAINT chk_provider_configurations_timeout_positive CHECK (timeout_ms > 0);

-- Retrying forever turns one slow provider into an outage for everything sharing its thread pool.
ALTER TABLE provider_configurations
    ADD CONSTRAINT chk_provider_configurations_retry_bounded CHECK (retry_count BETWEEN 0 AND 5);


-- Partner credentials, one row per provider.
--
-- SECURITY: these columns are NOT encrypted at rest by this migration. `encrypted` records the
-- intent for a given row, not an accomplished fact — nothing in Sprint 2 encrypts or decrypts.
-- Until a KMS-backed converter exists, treat this table as holding secrets in plaintext: restrict
-- database access accordingly, and never widen the admin API to return these values (it currently
-- reports only whether credentials are present — see ProviderCredentialsResponse).
CREATE TABLE provider_credentials
(
    id               UUID PRIMARY KEY,
    provider_id      UUID        NOT NULL,
    partner_email    TEXT,
    partner_password TEXT,
    partner_token    TEXT,
    encrypted        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_provider_credentials_provider
        FOREIGN KEY (provider_id)
            REFERENCES provider_configurations (id)
            ON DELETE CASCADE,

    -- One credential set per provider: two rows would leave "which one authenticates" ambiguous.
    CONSTRAINT uq_provider_credentials_provider UNIQUE (provider_id)
);

CREATE INDEX idx_provider_credentials_provider ON provider_credentials (provider_id);


-- FLIXBUS is already seeded by V5 and is deliberately left disabled: its base_url is still the
-- placeholder `flixbus.example.invalid`, and enabling it here would point this service's health
-- probe at a host that cannot resolve. Enabling is now a runtime operation
-- (POST /api/v1/providers/{id}/enable) once real credentials and a real base URL are configured —
-- which is precisely what this sprint's admin API exists for.
UPDATE provider_configurations
SET provider_category = 'BUS',
    timeout_ms        = 8000,
    retry_count       = 2,
    updated_at        = now()
WHERE provider_type = 'FLIXBUS';

UPDATE provider_configurations
SET provider_category = 'BUS',
    timeout_ms        = 5000,
    retry_count       = 1,
    updated_at        = now()
WHERE provider_type = 'MOCK';
