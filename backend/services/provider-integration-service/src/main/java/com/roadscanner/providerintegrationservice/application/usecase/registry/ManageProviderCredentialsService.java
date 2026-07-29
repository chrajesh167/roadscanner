package com.roadscanner.providerintegrationservice.application.usecase.registry;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotFoundException;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentialsId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderCredentialsRepository;

import java.time.Clock;
import java.util.Optional;

/**
 * Implements {@link ManageProviderCredentials}.
 *
 * <p>Verifies the provider exists before writing. Without that check a typo'd id would either
 * fail as a foreign-key violation surfacing as a 500, or — worse, if the constraint were ever
 * relaxed — leave a credential row orphaned and unreachable, with real secrets in it.
 *
 * <p>Storing is create-or-rotate rather than insert-only: V6 allows one credential set per
 * provider, so a second store call must replace the existing secrets, not collide with them.
 */
public class ManageProviderCredentialsService implements ManageProviderCredentials {

    private final ProviderCredentialsRepository credentialsRepository;
    private final ProviderConfigurationRepository providerRepository;
    private final Clock clock;

    public ManageProviderCredentialsService(ProviderCredentialsRepository credentialsRepository,
                                            ProviderConfigurationRepository providerRepository,
                                            Clock clock) {
        this.credentialsRepository = credentialsRepository;
        this.providerRepository = providerRepository;
        this.clock = clock;
    }

    @Override
    public ProviderCredentials store(StoreCredentialsCommand command) {
        requireProviderExists(command.providerId());

        ProviderCredentials credentials = credentialsRepository.findByProvider(command.providerId())
                .map(existing -> {
                    existing.rotate(command.partnerEmail(), command.partnerPassword(), command.partnerToken(),
                            clock.instant());
                    return existing;
                })
                .orElseGet(() -> ProviderCredentials.issue(
                        ProviderCredentialsId.generate(),
                        command.providerId(),
                        command.partnerEmail(),
                        command.partnerPassword(),
                        command.partnerToken(),
                        clock.instant()));

        return credentialsRepository.save(credentials);
    }

    @Override
    public Optional<CredentialsSummary> summarise(ProviderId providerId) {
        requireProviderExists(providerId);

        return credentialsRepository.findByProvider(providerId)
                .map(credentials -> new CredentialsSummary(
                        credentials.hasPassword(),
                        credentials.hasToken(),
                        credentials.isEncrypted(),
                        credentials.updatedAt()));
    }

    private void requireProviderExists(ProviderId providerId) {
        if (providerRepository.findById(providerId).isEmpty()) {
            throw new ProviderNotFoundException(providerId);
        }
    }
}
