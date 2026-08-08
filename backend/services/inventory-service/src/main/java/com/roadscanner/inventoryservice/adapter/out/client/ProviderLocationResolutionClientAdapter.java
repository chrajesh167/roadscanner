package com.roadscanner.inventoryservice.adapter.out.client;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.port.out.ProviderLocationResolutionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calls {@code search-service}'s internal resolution endpoint.
 *
 * <p>Read-only and unauthenticated, on the same {@code /internal} service-to-service surface every
 * other cross-service call on this platform uses.
 */
@Component
class ProviderLocationResolutionClientAdapter implements ProviderLocationResolutionClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderLocationResolutionClientAdapter.class);
    private static final String RESOLVE_PATH = "/internal/api/v1/provider-locations/{provider}/resolve";

    private final RestClient restClient;

    ProviderLocationResolutionClientAdapter(RestClient searchServiceRestClient) {
        this.restClient = searchServiceRestClient;
    }

    @Override
    public Map<UUID, String> resolveCityIds(ProviderType providerType, List<UUID> locationIds) {
        if (locationIds.isEmpty()) {
            return Map.of();
        }
        try {
            ResolutionResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(RESOLVE_PATH)
                            .queryParam("locationIds", locationIds)
                            .build(providerType.code()))
                    .retrieve()
                    .body(ResolutionResponse.class);

            if (response == null || response.cityIdsByLocation() == null) {
                log.warn("search-service returned no resolution body for provider {}", providerType);
                return Map.of();
            }

            Map<UUID, String> resolved = new LinkedHashMap<>();
            response.cityIdsByLocation().forEach((locationId, cityId) ->
                    resolved.put(UUID.fromString(locationId), cityId));

            if (response.unresolved() != null && !response.unresolved().isEmpty()) {
                // Named rather than counted: an operator fixing this needs to know which mapping to
                // author, and there is nothing else on the platform that will tell them.
                log.warn("Provider {} has no verified city mapping for locations {} — routes through "
                        + "them are skipped this cycle", providerType, response.unresolved());
            }
            return resolved;
        } catch (RestClientException e) {
            // Degrade, matching this service's other provider-facing port: an unreachable
            // search-service costs one sync cycle, not a failed synchronisation record.
            log.warn("Could not resolve provider locations for {} — skipping this cycle: {}",
                    providerType, e.getMessage());
            return Map.of();
        }
    }

    private record ResolutionResponse(String provider, Map<String, String> cityIdsByLocation,
                                       List<String> unresolved) {
    }
}
