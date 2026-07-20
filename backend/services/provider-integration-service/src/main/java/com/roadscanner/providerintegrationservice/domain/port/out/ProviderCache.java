package com.roadscanner.providerintegrationservice.domain.port.out;

import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;

import java.util.Optional;
import java.util.Set;

/** Redis-backed cache for two independent, differently-lived concerns — provider capability
 * metadata (long TTL, rarely changes) and seat maps (short TTL, changes on every block/release
 * anywhere). Kept as one port, not two, because both are "read-through in front of a provider
 * call, degrade to a live call on miss" — the same shape {@code search-service}'s
 * {@code AvailabilityCache} uses for its one concern. */
public interface ProviderCache {

    Optional<Set<ProviderCapability>> getCapabilities(ProviderType providerType);

    void putCapabilities(ProviderType providerType, Set<ProviderCapability> capabilities);

    Optional<ProviderSeatMap> getSeatMap(ProviderType providerType, String providerTripId);

    void putSeatMap(ProviderType providerType, String providerTripId, ProviderSeatMap seatMap);
}
