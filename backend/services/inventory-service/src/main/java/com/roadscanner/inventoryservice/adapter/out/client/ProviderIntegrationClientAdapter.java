package com.roadscanner.inventoryservice.adapter.out.client;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.port.out.ProviderIntegrationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements {@link ProviderIntegrationClient} over {@code provider-integration-service}'s
 * canonical internal REST API. This is the only class in this service that knows that API's
 * paths and shapes exist — every use case depends on the port only
 * (docs/services/inventory-service/boundaries.md).
 *
 * Session management is this adapter's own internal implementation detail, exactly as
 * docs/services/inventory-service/boundaries.md's "Relationship to `provider-integration-service`"
 * section describes: authenticate once per provider, reuse until near expiry, refresh. Held
 * in-memory (a plain {@link ConcurrentHashMap}, not Redis) — nothing in this service's design
 * documentation specifies a Redis-backed cache for this, and per the request that authorized this
 * implementation ("Redis only where explicitly documented"), introducing one here would be an
 * undocumented architecture decision, not a faithful implementation of one. A lost session on
 * restart just costs one re-authentication call, not a correctness issue.
 *
 * Every method degrades on failure rather than throwing, per the port's Javadoc.
 */
@Component
class ProviderIntegrationClientAdapter implements ProviderIntegrationClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderIntegrationClientAdapter.class);
    private static final String SESSIONS_PATH = "/internal/api/v1/providers/{providerType}/sessions";
    private static final String SEARCH_PATH = "/internal/api/v1/providers/{providerType}/sessions/{sessionId}/trips";
    private static final String SEAT_MAP_PATH =
            "/internal/api/v1/providers/{providerType}/sessions/{sessionId}/trips/{providerTripId}/seat-map";

    private final RestClient restClient;
    private final Clock clock;
    private final ConcurrentHashMap<ProviderType, CachedSession> sessions = new ConcurrentHashMap<>();

    ProviderIntegrationClientAdapter(RestClient providerIntegrationRestClient, Clock clock) {
        this.restClient = providerIntegrationRestClient;
        this.clock = clock;
    }

    @Override
    public List<ExternalProviderTrip> searchTrips(ProviderType providerType, String origin, String destination, LocalDate date) {
        Optional<String> sessionId = ensureSession(providerType);
        if (sessionId.isEmpty()) {
            return List.of();
        }
        try {
            SearchTripsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(SEARCH_PATH)
                            .queryParam("origin", origin)
                            .queryParam("destination", destination)
                            .queryParam("date", date)
                            .build(providerType.code(), sessionId.get()))
                    .retrieve()
                    .body(SearchTripsResponse.class);
            if (response == null || response.trips() == null) {
                return List.of();
            }
            return response.trips().stream()
                    .map(t -> new ExternalProviderTrip(t.providerTripId(), t.operatorName(), t.origin(), t.destination(),
                            t.departureTime(), t.arrivalTime(), t.busType(), t.fareAmount(), t.fareCurrency(), t.seatsAvailable()))
                    .toList();
        } catch (RestClientException e) {
            log.warn("Failed to search provider {} for trips {} -> {} on {} — degrading to no results",
                    providerType, origin, destination, date, e);
            return List.of();
        }
    }

    @Override
    public Optional<ExternalSeatLayout> getSeatLayout(ProviderType providerType, String providerTripId) {
        Optional<String> sessionId = ensureSession(providerType);
        if (sessionId.isEmpty()) {
            return Optional.empty();
        }
        try {
            SeatMapResponse response = restClient.get()
                    .uri(SEAT_MAP_PATH, providerType.code(), sessionId.get(), providerTripId)
                    .retrieve()
                    .body(SeatMapResponse.class);
            if (response == null || response.seats() == null) {
                return Optional.empty();
            }
            List<ExternalSeat> seats = response.seats().stream()
                    .map(s -> new ExternalSeat(s.seatNumber(), s.deck(), s.seatType()))
                    .toList();
            return Optional.of(new ExternalSeatLayout(providerTripId, seats));
        } catch (RestClientException e) {
            log.warn("Failed to fetch seat layout from provider {} for {} — degrading to absent",
                    providerType, providerTripId, e);
            return Optional.empty();
        }
    }

    @Override
    public OptionalInt getAvailableSeatCount(ProviderType providerType, String providerTripId) {
        Optional<String> sessionId = ensureSession(providerType);
        if (sessionId.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            SeatMapResponse response = restClient.get()
                    .uri(SEAT_MAP_PATH, providerType.code(), sessionId.get(), providerTripId)
                    .retrieve()
                    .body(SeatMapResponse.class);
            if (response == null || response.seats() == null) {
                return OptionalInt.empty();
            }
            long available = response.seats().stream().filter(s -> "AVAILABLE".equals(s.status())).count();
            return OptionalInt.of((int) available);
        } catch (RestClientException e) {
            log.warn("Failed to fetch live availability from provider {} for {} — degrading to unknown",
                    providerType, providerTripId, e);
            return OptionalInt.empty();
        }
    }

    private Optional<String> ensureSession(ProviderType providerType) {
        CachedSession cached = sessions.get(providerType);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            return Optional.of(cached.sessionId());
        }
        try {
            AuthenticateProviderResponse response = restClient.post()
                    .uri(SESSIONS_PATH, providerType.code())
                    .retrieve()
                    .body(AuthenticateProviderResponse.class);
            if (response == null) {
                return Optional.empty();
            }
            sessions.put(providerType, new CachedSession(response.sessionId().toString(), response.expiresAt()));
            return Optional.of(response.sessionId().toString());
        } catch (RestClientException e) {
            log.warn("Failed to authenticate against provider {} via provider-integration-service — degrading", providerType, e);
            return Optional.empty();
        }
    }

    private record CachedSession(String sessionId, Instant expiresAt) {
    }

    // --- Wire DTOs (provider-integration-service's own, already-shipped response shapes) -----

    private record AuthenticateProviderResponse(UUID sessionId, String providerType, Instant expiresAt) {
    }

    private record ProviderTripResponse(String providerTripId, String providerType, String operatorName,
                                         String origin, String destination, Instant departureTime, Instant arrivalTime,
                                         String busType, BigDecimal fareAmount, String fareCurrency, int seatsAvailable) {
    }

    private record SearchTripsResponse(List<ProviderTripResponse> trips) {
    }

    private record SeatResponse(String seatNumber, String deck, String seatType, String status,
                                 BigDecimal priceAmount, String priceCurrency) {
    }

    private record SeatMapResponse(String providerTripId, String providerType, List<SeatResponse> seats) {
    }
}
