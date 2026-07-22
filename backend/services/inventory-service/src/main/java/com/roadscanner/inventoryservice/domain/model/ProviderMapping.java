package com.roadscanner.inventoryservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The seam between this catalog and the live world — {@code Trip → ProviderType → providerTripId}
 * — and, per architecture review, the <strong>only</strong> bridge of its kind. At most one
 * {@code ProviderMapping} per {@link Trip}: a physical departure sold through two channels is two
 * distinct {@code Trip} rows, each with at most one mapping of its own, never one {@code Trip}
 * with multiple mappings (docs/services/inventory-service/domain-model.md).
 */
public final class ProviderMapping {

    private final TripId tripId;
    private final ProviderType providerType;
    private final String providerTripId;
    private Instant lastSyncedAt;
    private SyncStatus syncStatus;

    private ProviderMapping(TripId tripId, ProviderType providerType, String providerTripId,
                             Instant lastSyncedAt, SyncStatus syncStatus) {
        this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        if (providerTripId == null || providerTripId.isBlank()) {
            throw new IllegalArgumentException("providerTripId must not be blank");
        }
        this.providerTripId = providerTripId;
        this.lastSyncedAt = Objects.requireNonNull(lastSyncedAt, "lastSyncedAt must not be null");
        this.syncStatus = Objects.requireNonNull(syncStatus, "syncStatus must not be null");
    }

    public static ProviderMapping create(TripId tripId, ProviderType providerType, String providerTripId, Instant now) {
        return new ProviderMapping(tripId, providerType, providerTripId, now, SyncStatus.SUCCESS);
    }

    public static ProviderMapping reconstitute(TripId tripId, ProviderType providerType, String providerTripId,
                                                Instant lastSyncedAt, SyncStatus syncStatus) {
        return new ProviderMapping(tripId, providerType, providerTripId, lastSyncedAt, syncStatus);
    }

    public void recordSync(Instant syncedAt, SyncStatus status) {
        this.lastSyncedAt = Objects.requireNonNull(syncedAt, "syncedAt must not be null");
        this.syncStatus = Objects.requireNonNull(status, "status must not be null");
    }

    public TripId tripId() {
        return tripId;
    }

    public ProviderType providerType() {
        return providerType;
    }

    public String providerTripId() {
        return providerTripId;
    }

    public Instant lastSyncedAt() {
        return lastSyncedAt;
    }

    public SyncStatus syncStatus() {
        return syncStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProviderMapping other)) return false;
        return tripId.equals(other.tripId);
    }

    @Override
    public int hashCode() {
        return tripId.hashCode();
    }
}
