-- Durable session state — the source of truth TokenCache (Redis) sits in front of as a
-- short-TTL read-through cache. See docs/services/provider-integration-service/data-ownership.md.
--
-- Tokens are stored as-is (not hashed): unlike auth-service's own refresh tokens, this service
-- must send the access/refresh token back to the provider on every call, so a one-way hash
-- (useful for auth-service's "look up by presented token" pattern) would not work here. A real
-- production deployment would additionally encrypt these columns at rest (e.g. via pgcrypto or
-- an envelope-encryption proxy) — not implemented in this iteration; see README.md "Remaining
-- Integration Points".

CREATE TABLE provider_sessions
(
    id                UUID PRIMARY KEY,
    provider_type     VARCHAR(50)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    access_token      TEXT         NOT NULL,
    refresh_token     TEXT,
    token_type        VARCHAR(50)  NOT NULL,
    token_expires_at  TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_provider_sessions_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED'))
);

-- Backs ProviderHealthMonitorScheduler-adjacent sweeps and SessionExpirySweeper's "find sessions
-- past their token expiry" query.
CREATE INDEX idx_provider_sessions_status_expiry ON provider_sessions (status, token_expires_at);
