package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.out.ProviderMappingRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class ProviderMappingRepositoryAdapter implements ProviderMappingRepository {

    private final ProviderMappingSpringDataRepository springDataRepository;
    private final ProviderMappingMapper mapper = new ProviderMappingMapper();

    ProviderMappingRepositoryAdapter(ProviderMappingSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<ProviderMapping> findByTripId(TripId tripId) {
        return springDataRepository.findById(tripId.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<ProviderMapping> findByProviderTypeAndProviderTripId(ProviderType providerType, String providerTripId) {
        return springDataRepository.findByProviderTypeAndProviderTripId(providerType.code(), providerTripId)
                .map(mapper::toDomain);
    }

    @Override
    public ProviderMapping save(ProviderMapping mapping) {
        ProviderMappingJpaEntity entity = springDataRepository.findById(mapping.tripId().value())
                .map(existing -> {
                    mapper.applyTo(existing, mapping);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(mapping));
        return mapper.toDomain(springDataRepository.save(entity));
    }
}
