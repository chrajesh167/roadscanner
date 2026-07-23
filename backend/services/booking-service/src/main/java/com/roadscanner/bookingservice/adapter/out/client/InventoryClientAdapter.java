package com.roadscanner.bookingservice.adapter.out.client;

import com.roadscanner.bookingservice.domain.exception.UpstreamServiceUnavailableException;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.out.InventoryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Implements {@link InventoryClient} over {@code inventory-service}'s existing, frozen public
 * REST API (docs/services/booking-service/boundaries.md's "Relationship to `inventory-service`").
 * This is the only class in this service that knows that API's paths and shapes — every use case
 * depends on the port only.
 *
 * <p>Unlike {@code inventory-service}'s own client adapters, this one throws rather than
 * degrades: a {@code 404} maps to {@code Optional.empty()} (a genuine, expected "doesn't exist"
 * answer), but anything else (timeout, 5xx, connection failure) throws
 * {@link UpstreamServiceUnavailableException} — NFR-7 forbids proceeding with a hold or booking
 * against unverifiable catalog data.
 */
@Component
class InventoryClientAdapter implements InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClientAdapter.class);
    private static final String TRIP_PATH = "/api/v1/inventory/trips/{tripId}";
    private static final String SEAT_LAYOUT_PATH = "/api/v1/inventory/trips/{tripId}/seat-layout";
    private static final String PROVIDER_MAPPING_PATH = "/api/v1/inventory/trips/{tripId}/provider-mapping";

    private final RestClient restClient;

    InventoryClientAdapter(RestClient inventoryServiceRestClient) {
        this.restClient = inventoryServiceRestClient;
    }

    @Override
    public Optional<TripSnapshot> getTrip(TripId tripId) {
        try {
            TripResponse response = restClient.get()
                    .uri(TRIP_PATH, tripId.value())
                    .retrieve()
                    .body(TripResponse.class);
            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(new TripSnapshot(tripId, response.origin(), response.destination(),
                    response.departureTime(), response.arrivalTime(), response.operatorDisplayName(),
                    response.busTypeCategory(), response.amenities(), response.fareAmount(),
                    response.fareCurrency(), response.bookable()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw unavailable("inventory-service", "GET trip " + tripId, e);
        }
    }

    @Override
    public Optional<SeatLayoutView> getSeatLayout(TripId tripId) {
        try {
            SeatLayoutResponse response = restClient.get()
                    .uri(SEAT_LAYOUT_PATH, tripId.value())
                    .retrieve()
                    .body(SeatLayoutResponse.class);
            if (response == null || response.seats() == null) {
                return Optional.empty();
            }
            List<SeatShape> seats = response.seats().stream()
                    .map(s -> new SeatShape(s.seatNumber(), s.deck(), s.seatType(), s.wheelchairAccessible(),
                            s.position()))
                    .toList();
            return Optional.of(new SeatLayoutView(seats));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw unavailable("inventory-service", "GET seat layout for " + tripId, e);
        }
    }

    @Override
    public Optional<ProviderMappingView> getProviderMapping(TripId tripId) {
        try {
            ProviderMappingResponse response = restClient.get()
                    .uri(PROVIDER_MAPPING_PATH, tripId.value())
                    .retrieve()
                    .body(ProviderMappingResponse.class);
            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(new ProviderMappingView(new ProviderType(response.providerType()),
                    response.providerTripId()));
        } catch (HttpClientErrorException.NotFound e) {
            // A first-party trip with no provider equivalent — not an error.
            return Optional.empty();
        } catch (RestClientException e) {
            throw unavailable("inventory-service", "GET provider mapping for " + tripId, e);
        }
    }

    private UpstreamServiceUnavailableException unavailable(String service, String operation, Exception cause) {
        log.warn("{} failed calling {}: {}", operation, service, cause.getMessage());
        return new UpstreamServiceUnavailableException(service, operation);
    }

    // --- Wire DTOs (inventory-service's own, already-shipped response shapes) -----------------

    private record TripResponse(String tripId, String origin, String destination, Instant departureTime,
                                 Instant arrivalTime, String operatorId, String operatorDisplayName,
                                 String busTypeCategory, List<String> amenities, BigDecimal fareAmount,
                                 String fareCurrency, boolean bookable, String supplyOrigin) {
    }

    private record SeatShapeResponse(String seatNumber, String deck, String seatType, boolean wheelchairAccessible,
                                      Integer position) {
    }

    private record SeatLayoutResponse(String tripId, List<SeatShapeResponse> seats) {
    }

    private record ProviderMappingResponse(String tripId, String providerType, String providerTripId,
                                            Instant lastSyncedAt, String syncStatus) {
    }
}
