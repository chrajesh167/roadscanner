package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Implements {@link ProviderConfigurationRepository} over Postgres via JPA.
 *
 * <p>{@link #save} fetches-then-mutates for an existing row rather than always building a fresh
 * entity: handing Hibernate a detached entity assembled from scratch would defeat the
 * {@code @Version} column, letting two concurrent admin edits silently overwrite one another.
 */
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
    public Optional<Provider> findById(ProviderId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Provider> findAll() {
        return springDataRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Provider> findAllEnabled() {
        return springDataRepository.findByEnabledTrue().stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsByType(ProviderType type) {
        return springDataRepository.existsByProviderType(type.code());
    }

    @Override
    public Provider save(Provider provider) {
        ProviderConfigurationJpaEntity entity = springDataRepository.findById(provider.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, provider);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(provider));

        return mapper.toDomain(springDataRepository.save(entity));
    }
}
