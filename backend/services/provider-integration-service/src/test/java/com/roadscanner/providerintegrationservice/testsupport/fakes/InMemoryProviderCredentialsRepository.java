package com.roadscanner.providerintegrationservice.testsupport.fakes;

import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderCredentialsRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link ProviderCredentialsRepository} for application-layer tests. Keyed by provider,
 * mirroring V6's one-credential-set-per-provider constraint. */
public final class InMemoryProviderCredentialsRepository implements ProviderCredentialsRepository {

    private final Map<ProviderId, ProviderCredentials> stored = new LinkedHashMap<>();

    @Override
    public Optional<ProviderCredentials> findByProvider(ProviderId providerId) {
        return Optional.ofNullable(stored.get(providerId));
    }

    @Override
    public ProviderCredentials save(ProviderCredentials credentials) {
        stored.put(credentials.providerId(), credentials);
        return credentials;
    }

    public int count() {
        return stored.size();
    }
}
