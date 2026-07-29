package com.roadscanner.providerintegrationservice.domain.port.out;

import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link Provider} configuration rows.
 *
 * <p>This port was read-only until Sprint 2, when an admin API took over provider onboarding from
 * hand-written Flyway seeds. Registering a row still does not make a provider usable — an adapter
 * class implementing {@code ProviderClient} must exist and be resolvable by
 * {@code ProviderClientRegistry}, which remains a code change. What the API replaced is the SQL,
 * not the integration work.
 */
public interface ProviderConfigurationRepository {

    Optional<Provider> findByType(ProviderType type);

    Optional<Provider> findById(ProviderId id);

    List<Provider> findAll();

    List<Provider> findAllEnabled();

    /** Used to enforce one-row-per-provider-type before writing. */
    boolean existsByType(ProviderType type);

    Provider save(Provider provider);
}
