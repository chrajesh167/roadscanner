-- One row per provider, kept current by ProviderHealthMonitorScheduler. provider_type is the
-- primary key directly — there is never more than one health record per provider, so a
-- surrogate id would add nothing.

CREATE TABLE provider_health
(
    provider_type       VARCHAR(50) PRIMARY KEY,
    current_state       VARCHAR(20) NOT NULL,
    last_checked_at     TIMESTAMPTZ,
    last_success_at     TIMESTAMPTZ,
    last_failure_at     TIMESTAMPTZ,
    consecutive_failures INTEGER    NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT chk_provider_health_state CHECK (current_state IN ('UNKNOWN', 'HEALTHY', 'DEGRADED', 'UNAVAILABLE')),
    CONSTRAINT chk_provider_health_failures_non_negative CHECK (consecutive_failures >= 0)
);
