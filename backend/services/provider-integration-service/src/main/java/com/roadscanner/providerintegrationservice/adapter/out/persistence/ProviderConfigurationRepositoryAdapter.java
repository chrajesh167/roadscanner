package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class ProviderConfigurationRepositoryAdapter implements ProviderConfigurationRepository {

    private final ProviderConfigurationSpringDataRepository springDataRepository;
    private final ProviderConfigurationMapper mapper = new ProviderConfigurationMapper();

    ProviderConfigurationRepositoryAdapter(ProviderConfigurationSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<Provider> findByType(ProviderType type) {
        return springDataRepository.findByProviderType(type.code()).map(mapper::toDomain);
    }

    @Override
    public List<Provider> findAllEnabled() {
        return springDataRepository.findByEnabledTrue().stream().map(mapper::toDomain).toList();
    }
}
