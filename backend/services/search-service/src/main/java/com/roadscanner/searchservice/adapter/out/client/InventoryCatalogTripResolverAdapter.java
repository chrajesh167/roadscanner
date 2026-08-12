package com.roadscanner.searchservice.adapter.out.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import com.roadscanner.searchservice.domain.port.out.CatalogTripResolver;
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
 * Implements {@link CatalogTripResolver} over inventory-service's provider-trip resolution route.
 *
 * <p>One call per provider trip. That is acceptable because the fan-out is already bounded by how
 * many trips the providers returned for a single route and date, and it keeps the platform's one
 * identity bridge — inventory's {@code (provider_type, provider_trip_id)} mapping — as the only
 * place that translation lives. Copying the mapping into this service's read model would create a
 * second source of truth that drifts the moment a trip is re-synchronised.
 *
 * <p>Failures degrade per trip rather than per search: a 404 means catalog sync has not imported
 * that departure yet, and any other failure means inventory is unwell. Both leave the trip visible
 * and unbookable, which is true, instead of hiding a real provider trip or failing a search that
 * otherwise worked.
 */
@Component
class InventoryCatalogTripResolverAdapter implements CatalogTripResolver {

    private static final Logger log = LoggerFactory.getLogger(InventoryCatalogTripResolverAdapter.class);
    private static final String RESOLUTION_PATH =
            "/internal/api/v1/inventory/provider-trips/{providerType}/{providerTripId}";

    private final RestClient restClient;

    InventoryCatalogTripResolverAdapter(RestClient inventoryServiceRestClient) {
        this.restClient = inventoryServiceRestClient;
    }

    @Override
    public Map<String, UUID> resolveCatalogTripIds(List<ProviderTripResult> providerTrips) {
        Map<String, UUID> resolved = new LinkedHashMap<>();
        for (ProviderTripResult trip : providerTrips) {
            resolve(trip).ifPresent(tripId -> resolved.put(trip.providerTripId(), tripId));
        }
        return resolved;
    }

    private java.util.Optional<UUID> resolve(ProviderTripResult trip) {
        try {
            ProviderMappingDto mapping = restClient.get()
                    .uri(RESOLUTION_PATH, trip.providerCode(), trip.providerTripId())
                    .retrieve()
                    .body(ProviderMappingDto.class);

            if (mapping == null || mapping.tripId() == null) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(mapping.tripId());
        } catch (RestClientException e) {
            // Includes the 404 that means "not imported yet", which is ordinary rather than
            // exceptional — hence debug, not warn. Distinguishing it from a genuine outage would
            // need the status, and both outcomes are identical here: no catalog id, trip stays
            // visible and unbookable.
            log.debug("No catalog trip resolved for {} trip {} — treating as not yet bookable",
                    trip.providerCode(), trip.providerTripId(), e);
            return java.util.Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProviderMappingDto(UUID tripId, String providerType, String providerTripId) {
    }
}
