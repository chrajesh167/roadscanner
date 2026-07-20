package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/** The in-the-moment result of one probe against a provider (a single {@code ProviderClient.checkHealth()}
 * call) — transient, never persisted directly. {@link ProviderHealth} is the durable record this
 * feeds into, tracking state over time. */
public record ProviderHealthCheck(ProviderType providerType, HealthState state, Instant checkedAt, String message) {

    public ProviderHealthCheck {
        Objects.requireNonNull(providerType, "providerType must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(checkedAt, "checkedAt must not be null");
    }

    public boolean isHealthy() {
        return state == HealthState.HEALTHY;
    }
}
