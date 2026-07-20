package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** Persistence shape for {@code ProviderHealth} — {@code provider_type} is the primary key
 * directly (see {@code V4__create_provider_health.sql}): exactly one row per provider. */
@Entity
@Table(name = "provider_health")
public class ProviderHealthJpaEntity {

    @Id
    @Column(name = "provider_type", nullable = false, updatable = false)
    private String providerType;

    @Column(name = "current_state", nullable = false)
    private String currentState;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ProviderHealthJpaEntity() {
    }

    ProviderHealthJpaEntity(String providerType, String currentState, Instant lastCheckedAt, Instant lastSuccessAt,
                             Instant lastFailureAt, int consecutiveFailures, Instant updatedAt) {
        this.providerType = providerType;
        this.currentState = currentState;
        this.lastCheckedAt = lastCheckedAt;
        this.lastSuccessAt = lastSuccessAt;
        this.lastFailureAt = lastFailureAt;
        this.consecutiveFailures = consecutiveFailures;
        this.updatedAt = updatedAt;
    }

    void applyMutableState(String currentState, Instant lastCheckedAt, Instant lastSuccessAt, Instant lastFailureAt,
                            int consecutiveFailures, Instant updatedAt) {
        this.currentState = currentState;
        this.lastCheckedAt = lastCheckedAt;
        this.lastSuccessAt = lastSuccessAt;
        this.lastFailureAt = lastFailureAt;
        this.consecutiveFailures = consecutiveFailures;
        this.updatedAt = updatedAt;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getCurrentState() {
        return currentState;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
