package com.roadscanner.providerintegrationservice.application.usecase.search;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.port.in.SearchProviderTrips;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

import java.util.List;

/**
 * Implements {@link SearchProviderTrips}.
 *
 * <p>Thin by design. It resolves the registry row, refuses a provider that is switched off, checks
 * the search capability, and delegates. Everything that used to be worth writing here — timeout,
 * retries, backoff, metrics, correlation-id propagation — already happens in the execution layer
 * that wraps every {@code ProviderClient}, so duplicating any of it would create a second place
 * for the behaviour to drift.
 *
 * <p>A disabled provider is rejected before the call rather than after: an administrator who
 * disabled a provider expects traffic to stop immediately, not to keep flowing until the adapter
 * happens to fail.
 */
public class SearchProviderTripsService implements SearchProviderTrips {

    private final ProviderConfigurationRepository configurationRepository;
    private final ProviderClientRegistry registry;

    public SearchProviderTripsService(ProviderConfigurationRepository configurationRepository,
                                      ProviderClientRegistry registry) {
        this.configurationRepository = configurationRepository;
        this.registry = registry;
    }

    @Override
    public Result search(Command command) {
        Provider provider = configurationRepository.findByType(command.providerType())
                .orElseThrow(() -> new ProviderNotSupportedException(command.providerType()));

        if (!provider.enabled()) {
            throw new ProviderNotSupportedException(command.providerType());
        }

        ProviderClient client = registry.resolveWithCapability(provider.type(), ProviderCapability.SEARCH);
        List<ProviderTrip> trips = client.search(provider, command.criteria());
        return new Result(trips);
    }
}
