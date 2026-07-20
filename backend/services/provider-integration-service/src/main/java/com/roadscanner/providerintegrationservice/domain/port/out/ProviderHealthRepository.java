package com.roadscanner.providerintegrationservice.domain.port.out;

import com.roadscanner.providerintegrationservice.domain.model.ProviderHealth;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.util.Optional;

/** Persistence port for {@link ProviderHealth} — one row per {@link ProviderType}, kept current
 * by {@code ProviderHealthMonitorScheduler} and read by {@code CheckProviderHealth}. */
public interface ProviderHealthRepository {

    Optional<ProviderHealth> findByProviderType(ProviderType providerType);

    ProviderHealth save(ProviderHealth health);
}
