package com.roadscanner.inventoryservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_mappings")
public class ProviderMappingJpaEntity {

    @Id
    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "provider_type", nullable = false, updatable = false)
    private String providerType;

    @Column(name = "provider_trip_id", nullable = false, updatable = false)
    private String providerTripId;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    @Column(name = "sync_status", nullable = false)
    private String syncStatus;

    protected ProviderMappingJpaEntity() {
    }

    ProviderMappingJpaEntity(UUID tripId, String providerType, String providerTripId, Instant lastSyncedAt, String syncStatus) {
        this.tripId = tripId;
        this.providerType = providerType;
        this.providerTripId = providerTripId;
        this.lastSyncedAt = lastSyncedAt;
        this.syncStatus = syncStatus;
    }

    void applyMutableState(Instant lastSyncedAt, String syncStatus) {
        this.lastSyncedAt = lastSyncedAt;
        this.syncStatus = syncStatus;
    }

    public UUID getTripId() {
        return tripId;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getProviderTripId() {
        return providerTripId;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public String getSyncStatus() {
        return syncStatus;
    }
}
