package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderResponseException;
import com.roadscanner.providerintegrationservice.domain.model.CancellationResult;
import com.roadscanner.providerintegrationservice.domain.model.ContactDetail;
import com.roadscanner.providerintegrationservice.domain.model.FareAmount;
import com.roadscanner.providerintegrationservice.domain.model.HealthState;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealthCheck;
import com.roadscanner.providerintegrationservice.domain.model.ProviderOrder;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeat;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SeatAssignment;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Every FlixBus wire shape, and the translation between them and RoadScanner's provider-neutral
 * model. This is the only class that knows what FlixBus's JSON looks like.
 *
 * <p>All response records are {@code ignoreUnknown}: FlixBus adding a field must never break a
 * booking in progress.
 *
 * <p><strong>Naming.</strong> The DTO component names below are the literal JSON field names from
 * the documented API, including its mixed conventions — snake_case on the public search and
 * seat-map endpoints, camelCase on cart, checkout and payment. That inconsistency is FlixBus's, and
 * it is reproduced rather than tidied because a record component name <em>is</em> the wire field
 * name; renaming one for neatness silently sends a field the provider does not read.
 */
class FlixBusMapper {

    private static final String OPERATOR_NAME = "FlixBus";
    private static final DateTimeFormatter BIRTH_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** The only trip type this integration books — interconnections are multi-leg. */
    private static final String DIRECT_TRIP = "direct";

    private final Clock clock;

    FlixBusMapper(Clock clock) {
        this.clock = clock;
    }

    // ---------------------------------------------------------------- 1. partner login

    ProviderToken toSessionToken(PartnerLoginResponseDto dto, Instant expiresAt) {
        if (dto == null || dto.token() == null || dto.token().isBlank()) {
            throw new ProviderResponseException(ProviderType.FLIXBUS, "authenticate",
                    "Login response carried no session token", null);
        }
        // No refresh token exists in the documented API — renewal is a fresh login.
        return new ProviderToken(dto.token(), null, "Bearer", expiresAt);
    }

    ProviderHealthCheck toHealthyCheck(long startedAtNanos) {
        long millis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        return new ProviderHealthCheck(ProviderType.FLIXBUS, HealthState.HEALTHY, clock.instant(),
                "Partner login succeeded in " + millis + "ms");
    }

    // ---------------------------------------------------------------- 2. trip search

    /**
     * Maps a search response to provider-neutral trips.
     *
     * <p>Interconnection (multi-leg) items are dropped: this integration books a single ride, and
     * quoting a trip it cannot then book is worse than not showing it. Items whose legs are missing
     * are dropped for the same reason — without the three UUIDs the trip is unbookable.
     */
    List<ProviderTrip> toProviderTrips(TripSearchResponseDto dto, String currencyCode) {
        if (dto == null || dto.trips() == null) {
            return List.of();
        }
        Currency currency = Currency.getInstance(currencyCode);

        return dto.trips().stream()
                .filter(trip -> trip != null && trip.items() != null)
                .flatMap(trip -> trip.items().stream()
                        .filter(Objects::nonNull)
                        .filter(item -> DIRECT_TRIP.equals(item.type()))
                        .filter(item -> item.legs() != null && !item.legs().isEmpty())
                        .map(item -> toProviderTrip(trip, item, currency))
                        .filter(Objects::nonNull))
                .toList();
    }

    private ProviderTrip toProviderTrip(TripDto trip, TripItemDto item, Currency currency) {
        LegDto leg = item.legs().getFirst();
        if (leg == null || leg.ride_id() == null || leg.from_station_id() == null || leg.to_station_id() == null) {
            return null;
        }
        FlixBusTripUid uid = new FlixBusTripUid(leg.ride_id(), leg.from_station_id(), leg.to_station_id());

        return new ProviderTrip(
                uid.value(),
                ProviderType.FLIXBUS,
                OPERATOR_NAME,
                nameOf(trip.from()),
                nameOf(trip.to()),
                toInstant(item.departure()),
                toInstant(item.arrival()),
                // The provider's own service-tier wording ("semi-bed", "bed"). Passed through
                // rather than mapped onto a fixed vocabulary: an unrecognised tier must still
                // reach the traveller, not be flattened into a wrong one.
                item.transfer_type(),
                new FareAmount(toAmount(item.price_total_sum()), currency),
                item.available() == null ? 0 : item.available().seats(),
                leg.from_station_id(),
                leg.to_station_id());
    }

    // ---------------------------------------------------------------- 3. seat map

    /**
     * Maps the seat map, pricing each seat as the trip's base fare plus its category surcharge.
     *
     * <p>The surcharge is looked up by category name; a seat whose category has no matching entry
     * costs the base fare, which is the documented meaning of an absent surcharge rather than a
     * reason to fail.
     */
    ProviderSeatMap toProviderSeatMap(SeatMapResponseDto dto, FlixBusTripUid tripUid, BigDecimal tripBaseFare,
                                      String currencyCode) {
        if (dto == null) {
            throw new ProviderResponseException(ProviderType.FLIXBUS, "getSeatMap", "Empty seat-map response", null);
        }
        Currency currency = Currency.getInstance(currencyCode);
        BigDecimal baseFare = tripBaseFare == null ? BigDecimal.ZERO : tripBaseFare;

        Map<String, BigDecimal> surcharges = dto.seat_categories() == null ? Map.of()
                : dto.seat_categories().stream()
                        .filter(category -> category != null && category.category() != null)
                        .collect(java.util.stream.Collectors.toMap(SeatCategoryDto::category,
                                category -> category.price() == null ? BigDecimal.ZERO
                                        : toAmount(category.price().value()),
                                (first, second) -> first));

        List<ProviderSeat> seats = dto.seat_map() == null ? List.of()
                : dto.seat_map().stream()
                        .filter(deck -> deck != null && deck.seats() != null)
                        .flatMap(deck -> deck.seats().stream()
                                .filter(Objects::nonNull)
                                .map(seat -> toProviderSeat(seat, deck.deck(), baseFare, surcharges, currency)))
                        .toList();

        return new ProviderSeatMap(tripUid.value(), ProviderType.FLIXBUS, seats);
    }

    private ProviderSeat toProviderSeat(SeatDto seat, Integer deck, BigDecimal baseFare,
                                        Map<String, BigDecimal> surcharges, Currency currency) {
        BigDecimal surcharge = surcharges.getOrDefault(seat.category(), BigDecimal.ZERO);
        return new ProviderSeat(
                new SeatNumber(seat.label()),
                // Deck is an index in the payload; rendered as a name so no consumer has to know
                // that 0 means lower. Unknown indexes keep their number rather than being guessed.
                deck == null ? "UNKNOWN" : switch (deck) {
                    case 0 -> "LOWER";
                    case 1 -> "UPPER";
                    default -> "DECK_" + deck;
                },
                seat.seat_type() == null ? "UNSPECIFIED" : seat.seat_type(),
                Boolean.TRUE.equals(seat.is_available()) ? SeatStatus.AVAILABLE : SeatStatus.UNAVAILABLE,
                new FareAmount(baseFare.add(surcharge), currency));
    }

    /**
     * The per-seat provider id, keyed by the label a caller asks for.
     *
     * <p>Reserving a seat requires the provider's own seat id, which appears only in the seat map —
     * there is no lookup from a label. This is how a caller's "seat 12" becomes something FlixBus
     * will accept.
     */
    Map<String, String> toSeatIdsByLabel(SeatMapResponseDto dto) {
        if (dto == null || dto.seat_map() == null) {
            return Map.of();
        }
        return dto.seat_map().stream()
                .filter(deck -> deck != null && deck.seats() != null)
                .flatMap(deck -> deck.seats().stream())
                .filter(seat -> seat != null && seat.label() != null && seat.seat_id() != null)
                .collect(java.util.stream.Collectors.toMap(SeatDto::label, SeatDto::seat_id,
                        (first, second) -> first));
    }

    // ---------------------------------------------------------------- 4-6. cart

    String toCartId(CartResponseDto dto) {
        if (dto == null || dto.id() == null || dto.id().isBlank()) {
            throw new ProviderResponseException(ProviderType.FLIXBUS, "createCart",
                    "Cart response carried no cart id", null);
        }
        return dto.id();
    }

    AddTicketsRequestDto toAddTicketsRequest(FlixBusTripUid tripUid, int adults, int children) {
        return new AddTicketsRequestDto(tripUid.value(), new PassengerTypesDto(adults, children));
    }

    List<String> toTicketIds(CartItemsResponseDto dto) {
        if (dto == null || dto.items() == null) {
            return List.of();
        }
        return dto.items().stream()
                .filter(item -> item != null && item.product() != null)
                .filter(item -> "ticket".equals(item.product().type()))
                .map(item -> item.product().id())
                .filter(Objects::nonNull)
                .toList();
    }

    SeatReservationRequestDto toSeatReservationRequest(FlixBusTripUid tripUid, List<SeatAssignment> assignments,
                                                       List<PassengerDetail> passengers) {
        List<ReservationDto> reservations = java.util.stream.IntStream.range(0, assignments.size())
                .mapToObj(i -> {
                    SeatAssignment assignment = assignments.get(i);
                    return new ReservationDto(
                            assignment.providerTicketId(),
                            List.of(new ReservedSeatDto(assignment.providerSeatId(), tripUid.rideId())),
                            passengers.get(i).gender().wireValue(),
                            false);
                })
                .toList();
        return new SeatReservationRequestDto(reservations);
    }

    BigDecimal toReservedTotal(SeatReservationResponseDto dto) {
        if (dto == null || dto.price() == null || dto.price().amount() == null) {
            throw new ProviderResponseException(ProviderType.FLIXBUS, "reserveSeats",
                    "Seat reservation response carried no price", null);
        }
        return toAmount(dto.price().amount());
    }

    // ---------------------------------------------------------------- 7-8. checkout

    CheckoutRequestDto toCheckoutRequest(String cartId, ContactDetail contact, List<String> ticketIds,
                                         List<PassengerDetail> passengers, String defaultCallingCode) {
        List<CheckoutPassengerDto> checkoutPassengers = java.util.stream.IntStream.range(0, ticketIds.size())
                .mapToObj(i -> {
                    PassengerDetail passenger = passengers.get(i);
                    return new CheckoutPassengerDto(ticketIds.get(i), new PassengerNameDto(
                            passenger.firstName(), passenger.lastName(),
                            BIRTH_DATE.format(passenger.birthDate()), passenger.gender().wireValue()));
                })
                .toList();

        return new CheckoutRequestDto(cartId,
                new ContactDto(contact.phoneInInternationalFormat(defaultCallingCode), contact.email(),
                        contact.communicationPreference().wireValue()),
                checkoutPassengers);
    }

    String toCheckoutId(CheckoutResponseDto dto) {
        if (dto == null || dto.id() == null || dto.id().isBlank()) {
            throw new ProviderResponseException(ProviderType.FLIXBUS, "checkout",
                    "Checkout response carried no checkout id", null);
        }
        return dto.id();
    }

    /**
     * Reads a checkout, tolerating the several places the fare may appear.
     *
     * <p>The documented response puts the total under {@code price.total}, but the reference
     * implementation checks four further locations. All are checked here: a fare read as zero
     * because it sat one field over is a silent money bug, and the cost of looking is nil.
     */
    CheckoutDetails toCheckoutDetails(CheckoutDetailsResponseDto dto) {
        if (dto == null) {
            throw new ProviderResponseException(ProviderType.FLIXBUS, "getCheckout",
                    "Empty checkout-details response", null);
        }
        BigDecimal total = firstPresent(
                dto.price() == null ? null : dto.price().total(),
                dto.price() == null ? null : dto.price().value(),
                dto.total(),
                dto.total_price(),
                dto.cart() == null || dto.cart().price() == null ? null : dto.cart().price().total());

        String orderId = dto.order() == null ? null : dto.order().id();
        String orderToken = dto.order() == null ? null : dto.order().token();
        return new CheckoutDetails(total, orderId, orderToken);
    }

    // ---------------------------------------------------------------- 10-11. order

    ProviderOrder toProviderOrder(String orderId, Map<String, Object> body) {
        return new ProviderOrder(orderId, ProviderType.FLIXBUS, body == null ? Map.of() : body);
    }

    CancellationRequestDto toCancellationRequest(String reason) {
        // Empty items = cancel the entire order, per the documented contract.
        return new CancellationRequestDto(List.of(), "cash",
                reason == null || reason.isBlank() ? "order cancelled by passenger request" : reason);
    }

    CancellationResult toCancellationResult(String orderId, CancellationResponseDto dto) {
        if (dto == null || dto.refund() == null || dto.refund().amount() == null) {
            // A cancellation without a refund figure is a failed cancellation, not a zero refund.
            throw new ProviderResponseException(ProviderType.FLIXBUS, "cancelBooking",
                    "Cancellation response carried no refund amount", null);
        }
        return new CancellationResult(orderId,
                new FareAmount(toAmount(dto.refund().amount()), Currency.getInstance("INR")), clock.instant());
    }

    // ---------------------------------------------------------------- helpers

    private static String nameOf(PlaceDto place) {
        return place == null || place.name() == null || place.name().isBlank() ? "Unknown" : place.name();
    }

    private static Instant toInstant(TimestampDto dto) {
        if (dto == null || dto.timestamp() == null) {
            throw new ProviderResponseException(ProviderType.FLIXBUS, "search",
                    "Trip carried no departure/arrival timestamp", null);
        }
        // Epoch seconds, per the documented payload.
        return Instant.ofEpochSecond(dto.timestamp());
    }

    private static BigDecimal toAmount(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private static BigDecimal firstPresent(Double... candidates) {
        return java.util.Arrays.stream(candidates)
                .filter(Objects::nonNull)
                .findFirst()
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------- wire DTOs

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PartnerLoginResponseDto(String token) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TripSearchResponseDto(List<TripDto> trips) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TripDto(PlaceDto from, PlaceDto to, List<TripItemDto> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlaceDto(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TripItemDto(String type, String transfer_type, Double price_total_sum, AvailabilityDto available,
                       TimestampDto departure, TimestampDto arrival, List<LegDto> legs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AvailabilityDto(int seats) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TimestampDto(Long timestamp) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LegDto(String ride_id, String from_station_id, String to_station_id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeatMapResponseDto(List<SeatCategoryDto> seat_categories, List<SeatDeckDto> seat_map) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeatCategoryDto(String category, SeatPriceDto price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeatPriceDto(Double value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeatDeckDto(Integer deck, List<SeatDto> seats) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeatDto(String label, Boolean is_available, String category, String seat_type, String seat_id,
                   SeatPositionDto position, List<String> allowed_genders) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeatPositionDto(Integer row, Integer column) {
    }

    record CreateCartRequestDto(String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CartResponseDto(String id) {
    }

    record AddTicketsRequestDto(String trip, PassengerTypesDto passengerTypes) {
    }

    record PassengerTypesDto(int adult, int children) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CartItemsResponseDto(List<CartItemDto> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CartItemDto(CartProductDto product) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CartProductDto(String type, String id) {
    }

    record SeatReservationRequestDto(List<ReservationDto> reservations) {
    }

    record ReservationDto(String ticket, List<ReservedSeatDto> seats, String gender, boolean extraSeat) {
    }

    record ReservedSeatDto(String id, String ride) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SeatReservationResponseDto(ReservationPriceDto price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReservationPriceDto(Double amount) {
    }

    record CheckoutRequestDto(String cart, ContactDto contact, List<CheckoutPassengerDto> passengers) {
    }

    record ContactDto(String phone, String email, String communicationPreference) {
    }

    record CheckoutPassengerDto(String ticket, PassengerNameDto passenger) {
    }

    record PassengerNameDto(String firstName, String lastName, String birthdate, String gender) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CheckoutResponseDto(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CheckoutDetailsResponseDto(CheckoutPriceDto price, OrderRefDto order, Double total, Double total_price,
                                      CheckoutCartDto cart) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CheckoutPriceDto(Double total, Double value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CheckoutCartDto(CheckoutPriceDto price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderRefDto(String id, String token) {
    }

    record PaymentRequestDto(String checkoutId, String psp, String method, String externalPaymentReference) {
    }

    record CancellationRequestDto(List<String> items, String refundType, String context) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CancellationResponseDto(RefundDto refund) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RefundDto(Double amount) {
    }

    /** A checkout read, reduced to what the booking flow acts on. */
    record CheckoutDetails(BigDecimal total, String orderId, String orderToken) {

        boolean hasOrder() {
            return orderId != null && !orderId.isBlank();
        }

        Optional<String> orderTokenIfPresent() {
            return Optional.ofNullable(orderToken);
        }
    }
}
