package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.ProviderHealth;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderHealthRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class ProviderHealthRepositoryAdapter implements ProviderHealthRepository {

    private final ProviderHealthSpringDataRepository springDataRepository;
    private final ProviderHealthMapper mapper = new ProviderHealthMapper();

    ProviderHealthRepositoryAdapter(ProviderHealthSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<ProviderHealth> findByProviderType(ProviderType providerType) {
        return springDataRepository.findById(providerType.code()).map(mapper::toDomain);
    }

    @Override
    public ProviderHealth save(ProviderHealth health) {
        ProviderHealthJpaEntity entity = springDataRepository.findById(health.providerType().code())
                .map(existing -> {
                    mapper.applyTo(existing, health);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(health));

        return mapper.toDomain(springDataRepository.save(entity));
    }
}
