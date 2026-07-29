package com.roadscanner.searchservice.location.testsupport;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory {@link ProviderLocationMappingRepository} for application-layer tests. */
public final class InMemoryProviderLocationMappingRepository implements ProviderLocationMappingRepository {

    private final Map<ProviderLocationMappingId, ProviderLocationMapping> stored = new LinkedHashMap<>();

    @Override
    public Optional<ProviderLocationMapping> findByLocationAndProvider(LocationId locationId, ProviderCode provider) {
        return stored.values().stream()
                .filter(mapping -> mapping.locationId().equals(locationId) && mapping.provider().equals(provider))
                .findFirst();
    }

    @Override
    public List<ProviderLocationMapping> findByLocation(LocationId locationId) {
        return stored.values().stream()
                .filter(mapping -> mapping.locationId().equals(locationId))
                .toList();
    }

    @Override
    public Optional<ProviderLocationMapping> findByProviderCityId(ProviderCode provider, String providerCityId) {
        return stored.values().stream()
                .filter(mapping -> mapping.provider().equals(provider)
                        && Objects.equals(mapping.placeRef().cityId(), providerCityId))
                .findFirst();
    }

    @Override
    public Optional<ProviderLocationMapping> findByProviderStationId(ProviderCode provider, String providerStationId) {
        return stored.values().stream()
                .filter(mapping -> mapping.provider().equals(provider)
                        && Objects.equals(mapping.placeRef().stationId(), providerStationId))
                .findFirst();
    }

    @Override
    public ProviderLocationMapping save(ProviderLocationMapping mapping) {
        stored.put(mapping.id(), mapping);
        return mapping;
    }

    public void seed(ProviderLocationMapping... mappings) {
        for (ProviderLocationMapping mapping : mappings) {
            stored.put(mapping.id(), mapping);
        }
    }
}
