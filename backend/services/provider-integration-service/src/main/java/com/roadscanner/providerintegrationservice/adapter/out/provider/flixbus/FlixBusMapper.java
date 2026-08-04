package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.FareAmount;
import com.roadscanner.providerintegrationservice.domain.model.HealthState;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealthCheck;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeat;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTicket;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.ReservationId;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;
import com.roadscanner.providerintegrationservice.domain.model.TicketFormat;
import com.roadscanner.providerintegrationservice.domain.model.TicketId;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Translates between RoadScanner's canonical domain model and FlixBus's own wire shapes, and is
 * the single documented reference for that wire contract — see decision #3 in
 * {@code docs/services/provider-integration-service/}: RoadScanner has no real FlixBus B2B API
 * access, so this contract is one I've defined myself (below), self-consistent and fully
 * implemented/tested against {@code MockRestServiceServer}, swappable via
 * {@link FlixBusProperties} the moment a real one is available. All JSON fields are
 * {@code lowerCamelCase}; all instants are ISO-8601 UTC; all money fields are a nested
 * {@code {amount, currency}} object.
 *
 * <pre>
 * POST   {baseUrl}/oauth/token                                  → {@link TokenResponseDto}
 * GET    {baseUrl}/v1/trips?origin=&amp;destination=&amp;date=          → {@link TripsResponseDto}
 * GET    {baseUrl}/v1/trips/{tripId}/seatmap                    → {@link SeatMapResponseDto}
 * POST   {baseUrl}/v1/trips/{tripId}/seat-blocks                → {@link BlockResponseDto}
 * DELETE {baseUrl}/v1/seat-blocks/{blockReference}               → 204 No Content
 * POST   {baseUrl}/v1/seat-blocks/{blockReference}/booking       → {@link BookingResponseDto}
 * GET    {baseUrl}/v1/bookings/{bookingReference}/ticket         → {@link TicketResponseDto}
 * GET    {baseUrl}/v1/health                                     → {@link HealthResponseDto}
 * </pre>
 */
final class FlixBusMapper {

    private final Clock clock;

    FlixBusMapper(Clock clock) {
        this.clock = clock;
    }

    ProviderToken toProviderToken(TokenResponseDto dto) {
        Instant expiresAt = clock.instant().plusSeconds(dto.expiresInSeconds());
        return new ProviderToken(dto.accessToken(), dto.refreshToken(), dto.tokenType(), expiresAt);
    }

    /**
     * Secrets come from the encrypted credential store, never from configuration — an operator
     * rotating a partner secret through the admin API must take effect without a redeploy.
     *
     * <p>ASSUMPTION, inherited from an earlier sprint: the grant-type shape below. The supplied
     * contract specifies partner login as an email/password POST to
     * {@code /public/v1/partner/authenticate.json}; reconciling the two is Sprint 4's work, which
     * is why nothing here was redesigned.
     */
    TokenRequestDto toClientCredentialsRequest(FlixBusCredentials.PartnerLogin login) {
        return new TokenRequestDto("client_credentials", login.email(), login.password(), null);
    }

    TokenRequestDto toRefreshTokenRequest(FlixBusCredentials.PartnerLogin login, String refreshToken) {
        return new TokenRequestDto("refresh_token", login.email(), login.password(), refreshToken);
    }

    List<ProviderTrip> toProviderTrips(TripsResponseDto dto) {
        return dto.trips().stream().map(this::toProviderTrip).toList();
    }

    private ProviderTrip toProviderTrip(TripDto dto) {
        // Boarding/alighting identifiers are left null: the DTO this adapter currently binds does
        // not carry them, and inventing values a provider never sent would be worse than reporting
        // their absence. Sprint 3B populates them from the real response shape.
        return new ProviderTrip(dto.tripId(), ProviderType.FLIXBUS, dto.operator(), dto.origin(), dto.destination(),
                dto.departureTimeUtc(), dto.arrivalTimeUtc(), dto.busType(), toFareAmount(dto.fare()),
                dto.seatsAvailable(), null, null);
    }

    ProviderSeatMap toProviderSeatMap(String providerTripId, SeatMapResponseDto dto) {
        List<ProviderSeat> seats = dto.seats().stream().map(this::toProviderSeat).toList();
        return new ProviderSeatMap(providerTripId, ProviderType.FLIXBUS, seats);
    }

    private ProviderSeat toProviderSeat(SeatDto dto) {
        return new ProviderSeat(new SeatNumber(dto.seatNumber()), dto.deck(), dto.seatType(),
                dto.status(), toFareAmount(dto.price()));
    }

    BlockRequestDto toBlockRequest(List<SeatNumber> seatNumbers) {
        return new BlockRequestDto(seatNumbers.stream().map(SeatNumber::value).toList());
    }

    SeatReservation toSeatReservation(String providerTripId, BlockResponseDto dto, List<SeatNumber> requestedSeats) {
        return SeatReservation.block(ReservationId.generate(), dto.blockReference(), providerTripId, requestedSeats,
                clock.instant(), dto.expiresAtUtc());
    }

    ConfirmRequestDto toConfirmRequest(String providerTripId, List<PassengerDetail> passengers) {
        List<PassengerDto> passengerDtos = passengers.stream()
                .map(p -> new PassengerDto(p.fullName(), p.age(), p.gender(), p.seatNumber().value()))
                .toList();
        return new ConfirmRequestDto(providerTripId, passengerDtos);
    }

    BookingConfirmation toBookingConfirmation(ReservationId reservationId, String providerTripId,
                                               List<PassengerDetail> passengers, BookingResponseDto dto) {
        return new BookingConfirmation(new BookingReference(dto.bookingReference()), reservationId, providerTripId,
                passengers, toFareAmount(dto.totalFare()), dto.confirmedAtUtc());
    }

    ProviderTicket toProviderTicket(BookingReference bookingReference, TicketResponseDto dto) {
        return new ProviderTicket(new TicketId(dto.ticketId()), bookingReference, dto.format(),
                Base64.getDecoder().decode(dto.contentBase64()), dto.issuedAtUtc());
    }

    ProviderHealthCheck toHealthCheck(HealthResponseDto dto) {
        HealthState state = "UP".equalsIgnoreCase(dto.status()) ? HealthState.HEALTHY : HealthState.UNAVAILABLE;
        return new ProviderHealthCheck(ProviderType.FLIXBUS, state, clock.instant(), dto.message());
    }

    private FareAmount toFareAmount(MoneyDto dto) {
        return new FareAmount(dto.amount(), Currency.getInstance(dto.currency()));
    }

    // --- Wire DTOs -----------------------------------------------------------------------

    /**
     * TODO(sprint-4): reconcile these field names with FlixBus's real partner-authentication
     * schema.
     *
     * <p>{@code clientId}/{@code clientSecret} now carry the partner <em>email</em> and
     * <em>password</em> resolved from {@code provider_credentials}, so the names no longer describe
     * their contents. Left unchanged deliberately: a record component name is the outbound JSON
     * field name, so renaming these would silently alter a provider-facing request body. Guessing a
     * provider's wire contract is exactly the failure mode this sprint was scoped to avoid — the
     * rename lands together with the real schema, verified against it, not before.
     *
     * <p>Only the names are stale. The values are correct and come from the encrypted store; no
     * secret reaches this DTO from configuration.
     */
    record TokenRequestDto(String grantType, String clientId, String clientSecret, String refreshToken) {
    }

    record TokenResponseDto(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
    }

    record MoneyDto(BigDecimal amount, String currency) {
        MoneyDto {
            Objects.requireNonNull(amount, "amount must not be null");
            Objects.requireNonNull(currency, "currency must not be null");
        }
    }

    record TripDto(String tripId, String operator, String origin, String destination, Instant departureTimeUtc,
                    Instant arrivalTimeUtc, String busType, MoneyDto fare, int seatsAvailable) {
    }

    record TripsResponseDto(List<TripDto> trips) {
        TripsResponseDto {
            trips = trips == null ? List.of() : List.copyOf(trips);
        }
    }

    record SeatDto(String seatNumber, String deck, String seatType,
                    com.roadscanner.providerintegrationservice.domain.model.SeatStatus status, MoneyDto price) {
    }

    record SeatMapResponseDto(String tripId, List<SeatDto> seats) {
        SeatMapResponseDto {
            seats = seats == null ? List.of() : List.copyOf(seats);
        }
    }

    record BlockRequestDto(List<String> seatNumbers) {
    }

    record BlockResponseDto(String blockReference, List<String> seatNumbers, Instant expiresAtUtc) {
    }

    record PassengerDto(String fullName, int age, String gender, String seatNumber) {
    }

    record ConfirmRequestDto(String tripId, List<PassengerDto> passengers) {
    }

    record BookingResponseDto(String bookingReference, MoneyDto totalFare, Instant confirmedAtUtc) {
    }

    record TicketResponseDto(String ticketId, TicketFormat format, String contentBase64, Instant issuedAtUtc) {
    }

    record HealthResponseDto(String status, String message) {
    }
}
