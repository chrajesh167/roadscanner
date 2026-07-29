package com.roadscanner.providerintegrationservice.application.usecase.registry;

import com.roadscanner.providerintegrationservice.domain.exception.DuplicateProviderException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotFoundException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviders;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;

import java.time.Clock;
import java.util.List;

/**
 * Implements {@link ManageProviders}.
 *
 * <p>Takes a {@link Clock} rather than calling {@code Instant.now()} so timestamps are assertable
 * in tests, matching search-service's location use cases.
 *
 * <p>Enable/disable skip the write when the aggregate reports no change, so a retried call neither
 * errors nor pointlessly bumps {@code updated_at} — and, because this entity is
 * {@code @Version}-mapped, avoids burning an optimistic-locking version on a no-op.
 */
public class ManageProvidersService implements ManageProviders {

    private final ProviderConfigurationRepository repository;
    private final Clock clock;

    public ManageProvidersService(ProviderConfigurationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public List<Provider> list(boolean enabledOnly) {
        return enabledOnly ? repository.findAllEnabled() : repository.findAll();
    }

    @Override
    public Provider get(ProviderId providerId) {
        return repository.findById(providerId)
                .orElseThrow(() -> new ProviderNotFoundException(providerId));
    }

    @Override
    public Provider register(RegisterProviderCommand command) {
        // Pre-checked so the caller gets a precise 409 instead of a raw constraint violation. The
        // unique constraint is still the real guarantee under concurrency.
        if (repository.existsByType(command.type())) {
            throw new DuplicateProviderException(command.type());
        }

        Provider provider = Provider.register(
                ProviderId.generate(),
                command.type(),
                command.category(),
                command.displayName(),
                command.capabilities(),
                command.baseUrl(),
                command.timeoutMillis(),
                command.retryCount(),
                clock.instant());

        return repository.save(provider);
    }

    @Override
    public Provider update(UpdateProviderCommand command) {
        Provider provider = get(command.providerId());

        provider.update(
                command.category(),
                command.displayName(),
                command.capabilities(),
                command.baseUrl(),
                command.timeoutMillis(),
                command.retryCount(),
                clock.instant());

        return repository.save(provider);
    }

    @Override
    public ToggleResult enable(ProviderId providerId) {
        Provider provider = get(providerId);
        boolean changed = provider.enable(clock.instant());
        return new ToggleResult(changed ? repository.save(provider) : provider, changed);
    }

    @Override
    public ToggleResult disable(ProviderId providerId) {
        Provider provider = get(providerId);
        boolean changed = provider.disable(clock.instant());
        return new ToggleResult(changed ? repository.save(provider) : provider, changed);
    }
}
