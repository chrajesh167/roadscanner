package com.roadscanner.inventoryservice.domain.exception;

import com.roadscanner.inventoryservice.domain.model.ProviderType;

/**
 * No catalog trip has been reconciled for this provider trip.
 *
 * <p>Ordinary, not exceptional: a provider can offer a departure that catalog sync has not imported
 * yet — the sync window is bounded, and a live search reaches further ahead than the catalog does.
 * The provider trip is real; it simply has no catalog identity yet, and therefore nothing bookable.
 */
public class ProviderTripNotMappedException extends InventoryServiceException {

    private final ProviderType providerType;
    private final String providerTripId;

    public ProviderTripNotMappedException(ProviderType providerType, String providerTripId) {
        super("No catalog trip mapped for " + providerType.code() + " trip " + providerTripId);
        this.providerType = providerType;
        this.providerTripId = providerTripId;
    }

    public ProviderType providerType() {
        return providerType;
    }

    public String providerTripId() {
        return providerTripId;
    }
}
