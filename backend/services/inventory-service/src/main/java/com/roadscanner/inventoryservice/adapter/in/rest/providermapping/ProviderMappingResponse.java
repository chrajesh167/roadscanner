package com.roadscanner.inventoryservice.adapter.in.rest.providermapping;

import com.roadscanner.inventoryservice.domain.model.ProviderMapping;

import java.time.Instant;

public record ProviderMappingResponse(String tripId, String providerType, String providerTripId,
                                       Instant lastSyncedAt, String syncStatus) {

    public static ProviderMappingResponse from(ProviderMapping mapping) {
        return new ProviderMappingResponse(mapping.tripId().toString(), mapping.providerType().code(),
                mapping.providerTripId(), mapping.lastSyncedAt(), mapping.syncStatus().name());
    }
}
