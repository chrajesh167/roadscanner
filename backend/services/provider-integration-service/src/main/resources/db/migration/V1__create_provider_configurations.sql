-- Provider Integration Service schema — provider configuration rows.
-- See docs/services/provider-integration-service/data-ownership.md.
--
-- provider_type is the natural key, not a surrogate one: a provider is looked up by type on
-- every request (ProviderClientRegistry resolves the adapter the same way), and there is never
-- more than one configuration row per provider.

CREATE TABLE provider_configurations
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    provider_type VARCHAR(50)  NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    capabilities  TEXT         NOT NULL,
    base_url      VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_provider_configurations_type UNIQUE (provider_type)
);
