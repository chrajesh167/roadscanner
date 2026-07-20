package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The durable, per-provider health record — one row per {@link ProviderType}, updated by
 * {@code ProviderHealthMonitorScheduler} on every scheduled probe. This is what
 * {@code CheckProviderHealth}'s REST endpoint reads, and what decides whether a status flip is
 * worth publishing a {@code ProviderUnavailable}/{@code ProviderRecovered} audit event: the
 * event only fires on a transition, not on every poll, which {@link #recordCheck} determines by
 * comparing against the current {@link #currentState}.
 */
public final class ProviderHealth {

    private final ProviderType providerType;
    private HealthState currentState;
    private Instant lastCheckedAt;
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
    private int consecutiveFailures;
    private Instant updatedAt;

    private ProviderHealth(ProviderType providerType, HealthState currentState, Instant lastCheckedAt,
                            Instant lastSuccessAt, Instant lastFailureAt, int consecutiveFailures, Instant updatedAt) {
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        this.currentState = Objects.requireNonNull(currentState, "currentState must not be null");
        this.lastCheckedAt = lastCheckedAt;
        this.lastSuccessAt = lastSuccessAt;
        this.lastFailureAt = lastFailureAt;
        this.consecutiveFailures = consecutiveFailures;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static ProviderHealth unknown(ProviderType providerType, Instant now) {
        return new ProviderHealth(providerType, HealthState.UNKNOWN, null, null, null, 0, now);
    }

    public static ProviderHealth reconstitute(ProviderType providerType, HealthState currentState,
                                               Instant lastCheckedAt, Instant lastSuccessAt, Instant lastFailureAt,
                                               int consecutiveFailures, Instant updatedAt) {
        return new ProviderHealth(providerType, currentState, lastCheckedAt, lastSuccessAt, lastFailureAt,
                consecutiveFailures, updatedAt);
    }

    /** @return {@code true} if this check flipped {@link #currentState} to a different value —
     * the signal callers use to decide whether to publish an unavailability/recovery event. */
    public boolean recordCheck(ProviderHealthCheck check) {
        Objects.requireNonNull(check, "check must not be null");
        boolean changed = this.currentState != check.state();
        this.currentState = check.state();
        this.lastCheckedAt = check.checkedAt();
        this.updatedAt = check.checkedAt();
        if (check.isHealthy()) {
            this.lastSuccessAt = check.checkedAt();
            this.consecutiveFailures = 0;
        } else {
            this.lastFailureAt = check.checkedAt();
            this.consecutiveFailures++;
        }
        return changed;
    }

    public ProviderType providerType() {
        return providerType;
    }

    public HealthState currentState() {
        return currentState;
    }

    public Instant lastCheckedAt() {
        return lastCheckedAt;
    }

    public Instant lastSuccessAt() {
        return lastSuccessAt;
    }

    public Instant lastFailureAt() {
        return lastFailureAt;
    }

    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
