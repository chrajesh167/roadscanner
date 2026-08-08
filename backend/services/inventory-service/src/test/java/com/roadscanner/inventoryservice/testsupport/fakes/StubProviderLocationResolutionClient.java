package com.roadscanner.inventoryservice.testsupport.fakes;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.port.out.ProviderLocationResolutionClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Resolves every location asked for to a deterministic provider city id, unless told otherwise. */
public final class StubProviderLocationResolutionClient implements ProviderLocationResolutionClient {

    /** Locations this provider cannot name. Empty by default. */
    public List<UUID> unresolvable = new ArrayList<>();

    /** What the last call actually asked to translate — the defect was in what got sent. */
    public List<UUID> requestedLocationIds = List.of();

    @Override
    public Map<UUID, String> resolveCityIds(ProviderType providerType, List<UUID> locationIds) {
        requestedLocationIds = List.copyOf(locationIds);
        Map<UUID, String> resolved = new LinkedHashMap<>();
        for (UUID locationId : locationIds) {
            if (!unresolvable.contains(locationId)) {
                resolved.put(locationId, "provider-city-" + locationId);
            }
        }
        return resolved;
    }
}
