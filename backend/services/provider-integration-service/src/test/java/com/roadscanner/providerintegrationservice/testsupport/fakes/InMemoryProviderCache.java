package com.roadscanner.providerintegrationservice.testsupport.fakes;

import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderCache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class InMemoryProviderCache implements ProviderCache {

    private final Map<ProviderType, Set<ProviderCapability>> capabilities = new LinkedHashMap<>();
    private final Map<String, ProviderSeatMap> seatMaps = new LinkedHashMap<>();

    @Override
    public Optional<Set<ProviderCapability>> getCapabilities(ProviderType providerType) {
        return Optional.ofNullable(capabilities.get(providerType));
    }

    @Override
    public void putCapabilities(ProviderType providerType, Set<ProviderCapability> value) {
        capabilities.put(providerType, value);
    }

    @Override
    public Optional<ProviderSeatMap> getSeatMap(ProviderType providerType, String providerTripId) {
        return Optional.ofNullable(seatMaps.get(providerType.code() + ":" + providerTripId));
    }

    @Override
    public void putSeatMap(ProviderType providerType, String providerTripId, ProviderSeatMap seatMap) {
        seatMaps.put(providerType.code() + ":" + providerTripId, seatMap);
    }
}
