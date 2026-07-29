package com.roadscanner.searchservice.location.adapter.out.persistence;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Implements {@link ProviderLocationMappingRepository} over Postgres via JPA. Package-private. */
@Repository
class ProviderLocationMappingRepositoryAdapter implements ProviderLocationMappingRepository {

    private final ProviderLocationMappingSpringDataRepository springDataRepository;
    private final ProviderLocationMappingMapper mapper = new ProviderLocationMappingMapper();

    ProviderLocationMappingRepositoryAdapter(ProviderLocationMappingSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<ProviderLocationMapping> findByLocationAndProvider(LocationId locationId, ProviderCode provider) {
        return springDataRepository.findByLocationIdAndProvider(locationId.value(), provider.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<ProviderLocationMapping> findByLocation(LocationId locationId) {
        return springDataRepository.findByLocationId(locationId.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ProviderLocationMapping> findByProviderCityId(ProviderCode provider, String providerCityId) {
        return springDataRepository.findByProviderAndProviderCityId(provider.value(), providerCityId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<ProviderLocationMapping> findByProviderStationId(ProviderCode provider, String providerStationId) {
        return springDataRepository.findByProviderAndProviderStationId(provider.value(), providerStationId)
                .map(mapper::toDomain);
    }

    @Override
    public ProviderLocationMapping save(ProviderLocationMapping mapping) {
        ProviderLocationMappingJpaEntity entity = springDataRepository.findById(mapping.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, mapping);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(mapping));

        return mapper.toDomain(springDataRepository.save(entity));
    }
}
