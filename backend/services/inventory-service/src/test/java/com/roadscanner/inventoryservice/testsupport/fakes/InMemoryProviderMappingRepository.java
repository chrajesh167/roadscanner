package com.roadscanner.inventoryservice.testsupport.fakes;

import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.out.ProviderMappingRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryProviderMappingRepository implements ProviderMappingRepository {

    private final Map<TripId, ProviderMapping> mappings = new LinkedHashMap<>();

    @Override
    public Optional<ProviderMapping> findByTripId(TripId tripId) {
        return Optional.ofNullable(mappings.get(tripId));
    }

    @Override
    public Optional<ProviderMapping> findByProviderTypeAndProviderTripId(ProviderType providerType, String providerTripId) {
        return mappings.values().stream()
                .filter(m -> m.providerType().equals(providerType) && m.providerTripId().equals(providerTripId))
                .findFirst();
    }

    @Override
    public ProviderMapping save(ProviderMapping mapping) {
        mappings.put(mapping.tripId(), mapping);
        return mapping;
    }
}
