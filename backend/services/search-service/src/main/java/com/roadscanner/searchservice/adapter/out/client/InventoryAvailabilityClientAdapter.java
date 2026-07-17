package com.roadscanner.searchservice.adapter.out.client;

import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.out.AvailabilityClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Implements the {@link AvailabilityClient} port over {@code inventory-service}'s "Trip
 * Availability Query" category (docs/architecture/api-inventory.md), via the {@link RestClient}
 * configured in {@code config.InventoryClientConfig} (base URL, connect/read timeouts).
 *
 * Every failure mode — timeout, connection refused, a 4xx/5xx response, an unparseable body —
 * is caught here and degrades to {@link AvailabilityStatus#unknown()}, per this port's Javadoc
 * and docs/services/search-service/boundaries.md's "degrade, not fail" rule. This is the one
 * outbound call in the entire service that must never propagate its failure as an exception —
 * every other adapter in this codebase (the repository, the cache) is allowed to let a genuine
 * infrastructure failure surface, but a missing seat count is never worth failing a search over.
 */
@Component
class InventoryAvailabilityClientAdapter implements AvailabilityClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryAvailabilityClientAdapter.class);
    private static final String AVAILABILITY_PATH = "/api/v1/inventory/trips/{tripId}/availability";

    private final RestClient restClient;

    InventoryAvailabilityClientAdapter(RestClient inventoryServiceRestClient) {
        this.restClient = inventoryServiceRestClient;
    }

    @Override
    public AvailabilityStatus fetchAvailability(TripId tripId) {
        try {
            InventoryAvailabilityResponse response = restClient.get()
                    .uri(AVAILABILITY_PATH, tripId.value())
                    .retrieve()
                    .body(InventoryAvailabilityResponse.class);

            if (response == null) {
                log.warn("inventory-service returned an empty availability response for trip {}", tripId);
                return AvailabilityStatus.unknown();
            }
            return AvailabilityStatus.of(response.availableSeats());
        } catch (RestClientException e) {
            log.warn("Failed to fetch live availability for trip {} from inventory-service — degrading to unknown", tripId, e);
            return AvailabilityStatus.unknown();
        }
    }
}
