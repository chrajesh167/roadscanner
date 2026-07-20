package com.roadscanner.providerintegrationservice.testsupport.fakes;

import com.roadscanner.providerintegrationservice.domain.model.ProviderHealth;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderHealthRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryProviderHealthRepository implements ProviderHealthRepository {

    private final Map<ProviderType, ProviderHealth> healthByType = new LinkedHashMap<>();

    @Override
    public Optional<ProviderHealth> findByProviderType(ProviderType providerType) {
        return Optional.ofNullable(healthByType.get(providerType));
    }

    @Override
    public ProviderHealth save(ProviderHealth health) {
        healthByType.put(health.providerType(), health);
        return health;
    }
}
