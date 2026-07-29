package com.roadscanner.providerintegrationservice.testsupport.fakes;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link ProviderConfigurationRepository} for application-layer tests.
 *
 * <p>Keyed by {@link ProviderId} rather than {@link ProviderType} since Sprint 2, because the
 * admin API addresses providers by id. Type lookup is still supported — it is how everything
 * provider-facing resolves an adapter — but there is one map, so a save cannot leave the two
 * views disagreeing.
 */
public final class InMemoryProviderConfigurationRepository implements ProviderConfigurationRepository {

    private final Map<ProviderId, Provider> providers = new LinkedHashMap<>();

    public void add(Provider provider) {
        providers.put(provider.id(), provider);
    }

    @Override
    public Optional<Provider> findByType(ProviderType type) {
        return providers.values().stream().filter(provider -> provider.type().equals(type)).findFirst();
    }

    @Override
    public Optional<Provider> findById(ProviderId id) {
        return Optional.ofNullable(providers.get(id));
    }

    @Override
    public List<Provider> findAll() {
        return List.copyOf(providers.values());
    }

    @Override
    public List<Provider> findAllEnabled() {
        return providers.values().stream().filter(Provider::enabled).toList();
    }

    @Override
    public boolean existsByType(ProviderType type) {
        return findByType(type).isPresent();
    }

    @Override
    public Provider save(Provider provider) {
        providers.put(provider.id(), provider);
        return provider;
    }

    public int count() {
        return providers.size();
    }
}
