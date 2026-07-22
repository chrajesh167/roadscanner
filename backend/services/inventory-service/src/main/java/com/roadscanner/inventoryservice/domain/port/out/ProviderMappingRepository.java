package com.roadscanner.inventoryservice.domain.port.out;

import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.TripId;

import java.util.Optional;

public interface ProviderMappingRepository {

    Optional<ProviderMapping> findByTripId(TripId tripId);

    Optional<ProviderMapping> findByProviderTypeAndProviderTripId(ProviderType providerType, String providerTripId);

    ProviderMapping save(ProviderMapping mapping);
}
