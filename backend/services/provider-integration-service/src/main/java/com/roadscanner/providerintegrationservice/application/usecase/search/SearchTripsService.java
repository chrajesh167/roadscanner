package com.roadscanner.providerintegrationservice.application.usecase.search;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.port.in.SearchTrips;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

import java.util.List;

/** Implements {@link SearchTrips}. */
public class SearchTripsService implements SearchTrips {

    private final ActiveSessionResolver sessionResolver;
    private final ProviderClientRegistry registry;
    private final ProviderConfigurationRepository configurationRepository;

    public SearchTripsService(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry,
                              ProviderConfigurationRepository configurationRepository) {
        this.sessionResolver = sessionResolver;
        this.registry = registry;
        this.configurationRepository = configurationRepository;
    }

    @Override
    public Result search(Command command) {
        // Retained for callers that already hold a session. The session is still validated — it
        // is what scopes the request — but searching itself no longer requires one, so the call
        // goes through the same session-less client path every provider now uses.
        ProviderSession session = sessionResolver.resolveActive(command.sessionId());
        Provider provider = configurationRepository.findByType(session.providerType())
                .orElseThrow(() -> new ProviderNotSupportedException(session.providerType()));
        ProviderClient client = registry.resolveWithCapability(provider.type(), ProviderCapability.SEARCH);
        List<ProviderTrip> trips = client.search(provider, command.criteria());
        return new Result(trips);
    }
}
