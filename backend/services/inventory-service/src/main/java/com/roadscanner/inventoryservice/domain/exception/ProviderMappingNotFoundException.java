package com.roadscanner.inventoryservice.domain.exception;

import com.roadscanner.inventoryservice.domain.model.TripId;

/** The trip exists but has no {@code ProviderMapping} — either a pure first-party trip with no
 * provider equivalent, or a trip that hasn't been reconciled by catalog synchronization yet.
 * Callers of the availability facade treat this the same as "unavailable" (degrade, not fail —
 * docs/services/inventory-service/boundaries.md). */
public class ProviderMappingNotFoundException extends InventoryServiceException {

    private final TripId tripId;

    public ProviderMappingNotFoundException(TripId tripId) {
        super("No provider mapping for trip: " + tripId);
        this.tripId = tripId;
    }

    public TripId tripId() {
        return tripId;
    }
}
