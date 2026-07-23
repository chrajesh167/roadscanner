package com.roadscanner.bookingservice.adapter.out.client;

import com.roadscanner.bookingservice.domain.exception.SeatUnavailableException;
import com.roadscanner.bookingservice.domain.exception.UpstreamServiceUnavailableException;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Implements {@link ProviderIntegrationClient} over {@code provider-integration-service}'s
 * canonical, frozen internal REST API — the only class in this service that knows that API's
 * paths and shapes (docs/services/booking-service/boundaries.md's "Relationship to
 * `provider-integration-service`").
 *
 * <p>Session management mirrors {@code inventory-service}'s identical adapter: authenticate once
 * per provider, reuse until near expiry, refresh on a {@code 401} — held in-memory (a plain
 * {@link ConcurrentHashMap}, not Redis; nothing in this service's documentation specifies a
 * Redis-backed cache for this). Every method throws rather than degrades: a {@code 409} (seat
 * unavailable, or the provider declined confirmation —
 * {@code provider-integration-service}'s own {@code GlobalExceptionHandler} maps both
 * {@code SeatUnavailableException} and {@code BookingFailedException} to {@code 409}) becomes
 * this service's own {@link SeatUnavailableException}; anything else becomes
 * {@link UpstreamServiceUnavailableException}.
 */
@Component
class ProviderIntegrationClientAdapter implements ProviderIntegrationClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderIntegrationClientAdapter.class);
    private static final String SESSIONS_PATH = "/internal/api/v1/providers/{providerType}/sessions";
    private static final String SEAT_MAP_PATH =
            "/internal/api/v1/providers/{providerType}/sessions/{sessionId}/trips/{providerTripId}/seat-map";
    private static final String SEAT_BLOCKS_PATH =
            "/internal/api/v1/providers/{providerType}/sessions/{sessionId}/trips/{providerTripId}/seat-blocks";
    private static final String RELEASE_PATH =
            "/internal/api/v1/providers/{providerType}/sessions/{sessionId}/seat-blocks/{providerBlockReference}";
    private static final String CONFIRM_PATH =
            "/internal/api/v1/providers/{providerType}/sessions/{sessionId}/seat-blocks/{providerBlockReference}/booking";
    private static final String TICKET_PATH =
            "/internal/api/v1/providers/{providerType}/sessions/{sessionId}/bookings/{bookingReference}/ticket";

    private final RestClient restClient;
    private final Clock clock;
    private final ConcurrentHashMap<ProviderType, CachedSession> sessions = new ConcurrentHashMap<>();

    ProviderIntegrationClientAdapter(RestClient providerIntegrationRestClient, Clock clock) {
        this.restClient = providerIntegrationRestClient;
        this.clock = clock;
    }

    @Override
    public SeatMapView getSeatMap(ProviderType providerType, String providerTripId) {
        return withSession(providerType, sessionId -> {
            SeatMapResponse response = restClient.get()
                    .uri(SEAT_MAP_PATH, providerType.code(), sessionId, providerTripId)
                    .retrieve()
                    .body(SeatMapResponse.class);
            List<SeatStatusView> seats = response == null || response.seats() == null ? List.of()
                    : response.seats().stream()
                            .map(s -> new SeatStatusView(s.seatNumber(), s.deck(), s.seatType(), s.status(),
                                    s.priceAmount(), s.priceCurrency()))
                            .toList();
            return new SeatMapView(seats);
        }, providerType, "GET seat map for " + providerTripId);
    }

    @Override
    public Reservation blockSeats(ProviderType providerType, String providerTripId, List<String> seatNumbers) {
        return withSession(providerType, sessionId -> {
            SeatReservationResponse response = restClient.post()
                    .uri(SEAT_BLOCKS_PATH, providerType.code(), sessionId, providerTripId)
                    .body(new BlockSeatRequest(seatNumbers))
                    .retrieve()
                    .body(SeatReservationResponse.class);
            if (response == null) {
                throw new UpstreamServiceUnavailableException("provider-integration-service", "empty BlockSeat response");
            }
            return new Reservation(response.reservationId(), response.providerBlockReference(), response.seatNumbers(),
                    response.status(), response.blockedAt(), response.expiresAt());
        }, providerType, "block seats for " + providerTripId);
    }

    @Override
    public boolean releaseSeat(ProviderType providerType, String providerBlockReference) {
        return withSession(providerType, sessionId -> {
            ReleaseSeatResponse response = restClient.delete()
                    .uri(RELEASE_PATH, providerType.code(), sessionId, providerBlockReference)
                    .retrieve()
                    .body(ReleaseSeatResponse.class);
            return response != null && response.released();
        }, providerType, "release seat block " + providerBlockReference);
    }

    @Override
    public BookingConfirmationView confirmBooking(ProviderType providerType, String providerTripId,
                                                    String providerBlockReference, List<Passenger> passengers) {
        return withSession(providerType, sessionId -> {
            List<PassengerRequest> passengerRequests = passengers.stream()
                    .map(p -> new PassengerRequest(p.fullName(), p.age(), p.gender(), p.seatNumber()))
                    .toList();
            BookingConfirmationResponse response = restClient.post()
                    .uri(CONFIRM_PATH, providerType.code(), sessionId, providerBlockReference)
                    .body(new ConfirmBookingRequest(providerTripId, passengerRequests))
                    .retrieve()
                    .body(BookingConfirmationResponse.class);
            if (response == null) {
                throw new UpstreamServiceUnavailableException("provider-integration-service", "empty ConfirmBooking response");
            }
            return new BookingConfirmationView(response.bookingReference(), response.confirmedAt());
        }, providerType, "confirm booking for block " + providerBlockReference);
    }

    @Override
    public TicketView downloadTicket(ProviderType providerType, String providerBookingReference) {
        return withSession(providerType, sessionId -> {
            TicketResponse response = restClient.get()
                    .uri(TICKET_PATH, providerType.code(), sessionId, providerBookingReference)
                    .retrieve()
                    .body(TicketResponse.class);
            if (response == null) {
                throw new UpstreamServiceUnavailableException("provider-integration-service", "empty DownloadTicket response");
            }
            byte[] content = Base64.getDecoder().decode(response.contentBase64());
            return new TicketView(response.ticketId(), response.format(), content, response.issuedAt());
        }, providerType, "download ticket for booking " + providerBookingReference);
    }

    // --- Session lifecycle ----------------------------------------------------------------

    private <T> T withSession(ProviderType providerType, Function<String, T> operation, ProviderType logProviderType,
                               String description) {
        String sessionId = ensureSession(providerType);
        try {
            return operation.apply(sessionId);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("Session expired mid-call for provider {} — re-authenticating once and retrying", providerType);
            sessions.remove(providerType);
            String freshSessionId = ensureSession(providerType);
            try {
                return operation.apply(freshSessionId);
            } catch (RestClientException retryException) {
                throw translate(providerType, description, retryException);
            }
        } catch (RestClientException e) {
            throw translate(providerType, description, e);
        }
    }

    private String ensureSession(ProviderType providerType) {
        CachedSession cached = sessions.get(providerType);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            return cached.sessionId();
        }
        try {
            AuthenticateProviderResponse response = restClient.post()
                    .uri(SESSIONS_PATH, providerType.code())
                    .retrieve()
                    .body(AuthenticateProviderResponse.class);
            if (response == null) {
                throw new UpstreamServiceUnavailableException("provider-integration-service",
                        "empty authenticate response for " + providerType);
            }
            sessions.put(providerType, new CachedSession(response.sessionId().toString(), response.expiresAt()));
            return response.sessionId().toString();
        } catch (RestClientException e) {
            throw translate(providerType, "authenticate", e);
        }
    }

    private RuntimeException translate(ProviderType providerType, String description, RestClientException e) {
        if (e instanceof HttpClientErrorException.Conflict) {
            return new SeatUnavailableException(
                    "Provider " + providerType + " declined: " + description);
        }
        log.warn("provider-integration-service call failed ({} / {}): {}", providerType, description, e.getMessage());
        return new UpstreamServiceUnavailableException("provider-integration-service", description);
    }

    private record CachedSession(String sessionId, Instant expiresAt) {
    }

    // --- Wire DTOs (provider-integration-service's own, already-shipped request/response shapes) --

    private record AuthenticateProviderResponse(UUID sessionId, String providerType, Instant expiresAt) {
    }

    private record BlockSeatRequest(List<String> seatNumbers) {
    }

    private record SeatReservationResponse(String reservationId, String providerBlockReference, String providerTripId,
                                            List<String> seatNumbers, String status, Instant blockedAt,
                                            Instant expiresAt) {
    }

    private record ReleaseSeatResponse(boolean released) {
    }

    private record PassengerRequest(String fullName, int age, String gender, String seatNumber) {
    }

    private record ConfirmBookingRequest(String providerTripId, List<PassengerRequest> passengers) {
    }

    private record BookingConfirmationResponse(String bookingReference, String reservationId, String providerTripId,
                                                 List<String> passengerNames, BigDecimal totalFareAmount,
                                                 String totalFareCurrency, Instant confirmedAt) {
    }

    private record TicketResponse(String ticketId, String bookingReference, String format, String contentBase64,
                                   Instant issuedAt) {
    }

    private record SeatResponse(String seatNumber, String deck, String seatType, String status,
                                 BigDecimal priceAmount, String priceCurrency) {
    }

    private record SeatMapResponse(String providerTripId, String providerType, List<SeatResponse> seats) {
    }
}
