package com.roadscanner.providerintegrationservice.domain.port.out;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.util.List;
import java.util.Optional;

/** Persistence port for {@link Provider} configuration rows. Read-only from this service's own
 * use cases by design — providers are onboarded via a Flyway seed row plus a new adapter class
 * (see {@code ProviderClientRegistry}'s Javadoc), never created through this service's own API. */
public interface ProviderConfigurationRepository {

    Optional<Provider> findByType(ProviderType type);

    List<Provider> findAllEnabled();
}
