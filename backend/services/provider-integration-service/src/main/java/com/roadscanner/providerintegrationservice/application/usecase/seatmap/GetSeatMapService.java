package com.roadscanner.providerintegrationservice.application.usecase.seatmap;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.in.GetSeatMap;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderCache;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

/** Implements {@link GetSeatMap}, read-through against {@code ProviderCache} (short TTL — a seat
 * map is the most volatile thing this service ever caches, since it changes on every
 * concurrent block/release anywhere). */
public class GetSeatMapService implements GetSeatMap {

    private final ActiveSessionResolver sessionResolver;
    private final ProviderClientRegistry registry;
    private final ProviderCache providerCache;

    public GetSeatMapService(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry,
                              ProviderCache providerCache) {
        this.sessionResolver = sessionResolver;
        this.registry = registry;
        this.providerCache = providerCache;
    }

    @Override
    public Result getSeatMap(Command command) {
        ProviderSession session = sessionResolver.resolveActive(command.sessionId());

        ProviderSeatMap seatMap = providerCache.getSeatMap(session.providerType(), command.providerTripId())
                .orElseGet(() -> fetchAndCache(session, command.providerTripId()));

        return new Result(seatMap);
    }

    private ProviderSeatMap fetchAndCache(ProviderSession session, String providerTripId) {
        ProviderClient client = registry.resolveWithCapability(session.providerType(), ProviderCapability.SEAT_MAP);
        ProviderSeatMap seatMap = client.getSeatMap(session, providerTripId);
        providerCache.putSeatMap(session.providerType(), providerTripId, seatMap);
        return seatMap;
    }
}
