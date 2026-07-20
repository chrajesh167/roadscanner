package com.roadscanner.providerintegrationservice.application.usecase.search;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.port.in.SearchTrips;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

import java.util.List;

/** Implements {@link SearchTrips}. */
public class SearchTripsService implements SearchTrips {

    private final ActiveSessionResolver sessionResolver;
    private final ProviderClientRegistry registry;

    public SearchTripsService(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        this.sessionResolver = sessionResolver;
        this.registry = registry;
    }

    @Override
    public Result search(Command command) {
        ProviderSession session = sessionResolver.resolveActive(command.sessionId());
        ProviderClient client = registry.resolveWithCapability(session.providerType(), ProviderCapability.SEARCH);
        List<ProviderTrip> trips = client.search(session, command.criteria());
        return new Result(trips);
    }
}
