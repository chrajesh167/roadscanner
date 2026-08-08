package com.roadscanner.searchservice.location.adapter.in.rest;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.port.in.ResolveProviderLocations;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @param cityIdsByLocation the provider's own city id, keyed by canonical location id
 * @param unresolved        locations this provider has no verified city mapping for. Reported
 *                          explicitly so a caller can skip the route and say why, instead of
 *                          reading an absent key as an empty search result
 */
@Schema(name = "ProviderLocationResolution")
record ProviderLocationResolutionResponse(String provider,
                                          Map<String, String> cityIdsByLocation,
                                          List<String> unresolved) {

    static ProviderLocationResolutionResponse from(String provider, ResolveProviderLocations.Result result) {
        Map<String, String> cityIds = new LinkedHashMap<>();
        result.cityIdsByLocation().forEach((locationId, cityId) -> cityIds.put(locationId.value().toString(), cityId));

        return new ProviderLocationResolutionResponse(provider, cityIds,
                result.unresolved().stream().map(LocationId::value).map(java.util.UUID::toString).toList());
    }
}
