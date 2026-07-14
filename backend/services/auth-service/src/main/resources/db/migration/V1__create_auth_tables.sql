-- Auth Service schema — credentials, refresh-token sessions, password-reset requests.
-- See docs/services/auth-service/database-design.md for the conceptual data model.
--
-- This is the only service that ever writes to these tables — no other RoadScanner service
-- is granted access to this database (docs/architecture/database-ownership.md).

CREATE TABLE credentials
(
    id                    UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    login_identifier      VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    password_algorithm_id VARCHAR(50)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    last_login_at         TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_credentials_login_identifier UNIQUE (login_identifier),
    CONSTRAINT chk_credentials_status CHECK (status IN ('ACTIVE', 'LOCKED', 'SUSPENDED')),
    CONSTRAINT chk_credentials_failed_attempts CHECK (failed_login_attempts >= 0)
);

-- The hot path for every login attempt — see api-contract.md's "Login" operation.
-- UNIQUE already gives Postgres an index on login_identifier; no separate index needed.

CREATE TABLE refresh_tokens
(
    id                UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    token_hash        VARCHAR(255) NOT NULL,
    user_id           UUID         NOT NULL,
    issued_at         TIMESTAMPTZ  NOT NULL,
    expires_at        TIMESTAMPTZ  NOT NULL,
    revoked_at        TIMESTAMPTZ,
    replaces_token_id UUID,
    device_label      VARCHAR(255),
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES credentials (id),
    CONSTRAINT fk_refresh_tokens_replaces FOREIGN KEY (replaces_token_id) REFERENCES refresh_tokens (id),
    CONSTRAINT chk_refresh_tokens_expiry CHECK (expires_at > issued_at)
);

-- Backs both "logout everywhere" and reuse-detection family lookups — see
-- database-design.md's indexing note and RefreshTokenRepository.findByUserId.
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE password_reset_requests
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    token_hash VARCHAR(255) NOT NULL,
    user_id    UUID         NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_password_reset_requests_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_requests_user FOREIGN KEY (user_id) REFERENCES credentials (id)
);

-- No index on user_id here: unlike refresh_tokens, no port method
-- (PasswordResetRepository has only findByTokenHash) queries this table by user_id today.
-- Add one if/when that query pattern is actually needed — not speculatively.
