package com.roadscanner.providerintegrationservice.testsupport.fakes;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryProviderConfigurationRepository implements ProviderConfigurationRepository {

    private final Map<ProviderType, Provider> providers = new LinkedHashMap<>();

    public void add(Provider provider) {
        providers.put(provider.type(), provider);
    }

    @Override
    public Optional<Provider> findByType(ProviderType type) {
        return Optional.ofNullable(providers.get(type));
    }

    @Override
    public List<Provider> findAllEnabled() {
        return providers.values().stream().filter(Provider::enabled).toList();
    }
}
