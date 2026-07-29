package com.roadscanner.providerintegrationservice.domain.port.out;

import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;

import java.util.Optional;

/**
 * Persistence port for {@link ProviderCredentials}.
 *
 * <p>Lookup is by provider only — there is at most one credential set per provider, enforced by a
 * unique constraint in V6, because two would leave "which one authenticates" ambiguous.
 *
 * <p>No {@code findAll}, deliberately. Nothing has a legitimate reason to enumerate every secret
 * in the system, and the absence of the method is a cheaper guarantee than reviewing every caller
 * that might have used it.
 */
public interface ProviderCredentialsRepository {

    Optional<ProviderCredentials> findByProvider(ProviderId providerId);

    ProviderCredentials save(ProviderCredentials credentials);
}
