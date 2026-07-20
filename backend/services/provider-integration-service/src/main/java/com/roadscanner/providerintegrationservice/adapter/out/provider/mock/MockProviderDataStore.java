package com.roadscanner.providerintegrationservice.adapter.out.provider.mock;

import com.roadscanner.providerintegrationservice.domain.exception.BookingFailedException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderTripNotFoundException;
import com.roadscanner.providerintegrationservice.domain.exception.SeatUnavailableException;
import com.roadscanner.providerintegrationservice.domain.exception.TicketNotFoundException;
import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.FareAmount;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeat;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTicket;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.ReservationId;
import com.roadscanner.providerintegrationservice.domain.model.ReservationStatus;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;
import com.roadscanner.providerintegrationservice.domain.model.SeatStatus;
import com.roadscanner.providerintegrationservice.domain.model.TicketFormat;
import com.roadscanner.providerintegrationservice.domain.model.TicketId;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * In-memory state for {@link MockProviderClientAdapter} — trips, live seat status, reservations,
 * and bookings, all held in {@link ConcurrentHashMap}s (this adapter is a Spring singleton, so
 * every method must be safe under concurrent requests). Nothing here is persisted; state resets
 * on restart, which is the correct behavior for a stand-in that exists purely so the platform is
 * testable end-to-end before real provider credentials exist.
 *
 * Trips are generated deterministically from {@link SearchCriteria} on first search
 * ({@link #search}) via {@code computeIfAbsent}, keyed by a human-readable id derived from
 * origin/destination/date/bus-config — repeating the same search returns the same
 * {@code providerTripId}s with whatever live seat state has accumulated since, exactly like a
 * real provider would.
 */
final class MockProviderDataStore {

    private static final Duration BLOCK_TTL = Duration.ofMinutes(5);
    private static final LocalTime DEPARTURE_TIME = LocalTime.of(20, 0);
    private static final Duration TRIP_DURATION = Duration.ofHours(6);

    private final Clock clock;
    private final ConcurrentMap<String, TripRecord> trips = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReservationRecord> reservations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BookingRecord> bookings = new ConcurrentHashMap<>();

    MockProviderDataStore(Clock clock) {
        this.clock = clock;
    }

    List<ProviderTrip> search(SearchCriteria criteria) {
        List<ProviderTrip> results = new ArrayList<>();
        for (MockProviderFixtures.BusConfig config : MockProviderFixtures.BUS_CONFIGS) {
            String tripId = buildTripId(criteria, config);
            TripRecord record = trips.computeIfAbsent(tripId, id -> newTripRecord(id, criteria, config));
            results.add(record.toProviderTrip());
        }
        return results;
    }

    ProviderSeatMap getSeatMap(String providerTripId) {
        TripRecord record = requireTrip(providerTripId);
        return record.toSeatMap();
    }

    SeatReservation block(String providerTripId, List<SeatNumber> seatNumbers) {
        TripRecord record = requireTrip(providerTripId);
        for (SeatNumber seatNumber : seatNumbers) {
            SeatStatus status = record.seatStatus.get(seatNumber.value());
            if (status == null) {
                throw new SeatUnavailableException("No such seat " + seatNumber + " on trip " + providerTripId,
                        mockError("SEAT_NOT_FOUND", "No such seat: " + seatNumber));
            }
            if (status != SeatStatus.AVAILABLE) {
                throw new SeatUnavailableException("Seat " + seatNumber + " is not available (status=" + status + ")",
                        mockError("SEAT_UNAVAILABLE", "Seat " + seatNumber + " is not available"));
            }
        }
        seatNumbers.forEach(seatNumber -> record.seatStatus.put(seatNumber.value(), SeatStatus.BLOCKED));

        String blockReference = "MOCK-BLK-" + UUID.randomUUID();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(BLOCK_TTL);
        reservations.put(blockReference, new ReservationRecord(providerTripId, seatNumbers, expiresAt));

        ReservationId reservationId = ReservationId.generate();
        return SeatReservation.block(reservationId, blockReference, providerTripId, seatNumbers, now, expiresAt);
    }

    boolean release(String providerBlockReference) {
        ReservationRecord reservation = reservations.get(providerBlockReference);
        if (reservation == null || reservation.status != ReservationStatus.BLOCKED) {
            // Unknown or already-terminal reference — treated as an idempotent no-op, the same
            // way many real provider APIs avoid distinguishing "never existed" from "already
            // released" in their response.
            return false;
        }
        reservation.status = ReservationStatus.RELEASED;
        TripRecord trip = trips.get(reservation.providerTripId);
        if (trip != null) {
            reservation.seatNumbers.forEach(seatNumber -> trip.seatStatus.put(seatNumber.value(), SeatStatus.AVAILABLE));
        }
        return true;
    }

    BookingConfirmation confirm(String providerBlockReference, String providerTripId, List<PassengerDetail> passengers) {
        ReservationRecord reservation = reservations.get(providerBlockReference);
        Instant now = clock.instant();
        if (reservation == null || reservation.status != ReservationStatus.BLOCKED
                || !reservation.providerTripId.equals(providerTripId) || now.isAfter(reservation.expiresAt)) {
            throw new BookingFailedException("Seat block " + providerBlockReference + " is not confirmable",
                    mockError("BLOCK_NOT_CONFIRMABLE", "Seat block is missing, expired, or already used"));
        }
        reservation.status = ReservationStatus.CONFIRMED;
        TripRecord trip = requireTrip(providerTripId);
        reservation.seatNumbers.forEach(seatNumber -> trip.seatStatus.put(seatNumber.value(), SeatStatus.BOOKED));

        BookingReference bookingReference = new BookingReference("MOCK-BK-" + UUID.randomUUID());
        FareAmount seatFare = trip.seatTemplate.values().iterator().next().price();
        FareAmount totalFare = new FareAmount(
                trip.baseFare.multiply(java.math.BigDecimal.valueOf(passengers.size())), seatFare.currency());
        BookingConfirmation confirmation = new BookingConfirmation(bookingReference, reservation.reservationId(),
                providerTripId, passengers, totalFare, now);

        byte[] ticketContent = ("MOCK TICKET\nBooking: " + bookingReference + "\nTrip: " + providerTripId + "\n"
                + "Passengers: " + passengers.stream().map(PassengerDetail::fullName).collect(Collectors.joining(", ")))
                .getBytes(StandardCharsets.UTF_8);
        ProviderTicket ticket = new ProviderTicket(new TicketId(bookingReference.value()), bookingReference,
                TicketFormat.PDF, ticketContent, now);
        bookings.put(bookingReference.value(), new BookingRecord(confirmation, ticket));
        return confirmation;
    }

    ProviderTicket downloadTicket(BookingReference bookingReference) {
        BookingRecord booking = bookings.get(bookingReference.value());
        if (booking == null) {
            throw new TicketNotFoundException(bookingReference);
        }
        return booking.ticket;
    }

    private TripRecord requireTrip(String providerTripId) {
        TripRecord record = trips.get(providerTripId);
        if (record == null) {
            throw new ProviderTripNotFoundException(providerTripId);
        }
        return record;
    }

    private static String buildTripId(SearchCriteria criteria, MockProviderFixtures.BusConfig config) {
        return "MOCK-%s-%s-%s-%s".formatted(
                slug(criteria.origin()), slug(criteria.destination()), criteria.travelDate(), config.suffix());
    }

    private static String slug(String value) {
        return value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "");
    }

    private static ProviderError mockError(String code, String message) {
        return new ProviderError(ProviderType.MOCK, code, message, false);
    }

    private TripRecord newTripRecord(String providerTripId, SearchCriteria criteria, MockProviderFixtures.BusConfig config) {
        Instant departureTime = criteria.travelDate().atTime(DEPARTURE_TIME).atZone(ZoneOffset.UTC).toInstant();
        Instant arrivalTime = departureTime.plus(TRIP_DURATION);
        Map<String, ProviderSeat> seatTemplate = MockProviderFixtures.buildSeatTemplate(config);
        return new TripRecord(providerTripId, criteria.origin(), criteria.destination(), departureTime, arrivalTime,
                config.seatType(), config.baseFare(), seatTemplate);
    }

    /** Mutable per-trip state: the seat template's non-status fields never change, so only
     * current {@link SeatStatus} per seat is tracked separately and re-merged with the template
     * on every read — see {@link #toSeatMap()}. */
    private static final class TripRecord {
        private final String providerTripId;
        private final String origin;
        private final String destination;
        private final Instant departureTime;
        private final Instant arrivalTime;
        private final String busType;
        private final java.math.BigDecimal baseFare;
        private final Map<String, ProviderSeat> seatTemplate;
        private final ConcurrentMap<String, SeatStatus> seatStatus = new ConcurrentHashMap<>();

        private TripRecord(String providerTripId, String origin, String destination, Instant departureTime,
                            Instant arrivalTime, String busType, java.math.BigDecimal baseFare,
                            Map<String, ProviderSeat> seatTemplate) {
            this.providerTripId = providerTripId;
            this.origin = origin;
            this.destination = destination;
            this.departureTime = departureTime;
            this.arrivalTime = arrivalTime;
            this.busType = busType;
            this.baseFare = baseFare;
            this.seatTemplate = seatTemplate;
            seatTemplate.forEach((seatNumber, seat) -> seatStatus.put(seatNumber, seat.status()));
        }

        private ProviderTrip toProviderTrip() {
            long available = seatStatus.values().stream().filter(status -> status == SeatStatus.AVAILABLE).count();
            FareAmount fare = seatTemplate.values().iterator().next().price();
            return new ProviderTrip(providerTripId, ProviderType.MOCK, "Mock Travels", origin, destination,
                    departureTime, arrivalTime, busType, fare, (int) available);
        }

        private ProviderSeatMap toSeatMap() {
            List<ProviderSeat> seats = seatTemplate.values().stream()
                    .map(seat -> new ProviderSeat(seat.seatNumber(), seat.deck(), seat.seatType(),
                            seatStatus.get(seat.seatNumber().value()), seat.price()))
                    .toList();
            return new ProviderSeatMap(providerTripId, ProviderType.MOCK, seats);
        }
    }

    private static final class ReservationRecord {
        private final String providerTripId;
        private final List<SeatNumber> seatNumbers;
        private final Instant expiresAt;
        private final ReservationId reservationId = ReservationId.generate();
        private volatile ReservationStatus status = ReservationStatus.BLOCKED;

        private ReservationRecord(String providerTripId, List<SeatNumber> seatNumbers, Instant expiresAt) {
            this.providerTripId = providerTripId;
            this.seatNumbers = List.copyOf(seatNumbers);
            this.expiresAt = expiresAt;
        }

        private ReservationId reservationId() {
            return reservationId;
        }
    }

    private record BookingRecord(BookingConfirmation confirmation, ProviderTicket ticket) {
    }
}
