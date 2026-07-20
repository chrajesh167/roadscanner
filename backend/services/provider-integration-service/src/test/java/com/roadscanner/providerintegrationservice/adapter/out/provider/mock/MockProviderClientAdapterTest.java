package com.roadscanner.providerintegrationservice.adapter.out.provider.mock;

import com.roadscanner.providerintegrationservice.domain.exception.BookingFailedException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderTripNotFoundException;
import com.roadscanner.providerintegrationservice.domain.exception.SeatUnavailableException;
import com.roadscanner.providerintegrationservice.domain.exception.TicketNotFoundException;
import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeat;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTicket;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the full search → seat map → block → confirm → ticket happy path, plus release and
 * every failure mode the mock deliberately supports — see {@link MockProviderDataStore}'s Javadoc
 * for why this must behave like a real provider, not a trivial stub. */
class MockProviderClientAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);

    private final MockProviderClientAdapter adapter = new MockProviderClientAdapter(CLOCK);
    private final SearchCriteria criteria = new SearchCriteria("Mumbai", "Pune", LocalDate.of(2026, 8, 1));

    @Test
    void fullHappyPathFromSearchToTicket() {
        List<ProviderTrip> trips = adapter.search(null, criteria);
        assertThat(trips).isNotEmpty();
        String providerTripId = trips.get(0).providerTripId();

        ProviderSeatMap seatMap = adapter.getSeatMap(null, providerTripId);
        SeatNumber availableSeat = seatMap.seats().stream().filter(ProviderSeat::isAvailable).findFirst()
                .orElseThrow().seatNumber();

        SeatReservation reservation = adapter.blockSeats(null, providerTripId, List.of(availableSeat));
        assertThat(reservation.seatNumbers()).containsExactly(availableSeat);

        List<PassengerDetail> passengers = List.of(new PassengerDetail("Jane Doe", 30, "F", availableSeat));
        BookingConfirmation confirmation = adapter.confirmBooking(null, reservation.providerBlockReference(),
                providerTripId, passengers);
        assertThat(confirmation.passengers()).isEqualTo(passengers);

        ProviderTicket ticket = adapter.downloadTicket(null, confirmation.bookingReference());
        assertThat(ticket.bookingReference()).isEqualTo(confirmation.bookingReference());
        assertThat(ticket.content()).isNotEmpty();
    }

    @Test
    void searchIsDeterministicAcrossRepeatedCalls() {
        List<ProviderTrip> first = adapter.search(null, criteria);
        List<ProviderTrip> second = adapter.search(null, criteria);

        assertThat(first).extracting(ProviderTrip::providerTripId)
                .containsExactlyElementsOf(second.stream().map(ProviderTrip::providerTripId).toList());
    }

    @Test
    void releaseIsIdempotent() {
        String providerTripId = adapter.search(null, criteria).get(0).providerTripId();
        ProviderSeatMap seatMap = adapter.getSeatMap(null, providerTripId);
        SeatNumber seat = seatMap.seats().stream().filter(ProviderSeat::isAvailable).findFirst().orElseThrow().seatNumber();
        SeatReservation reservation = adapter.blockSeats(null, providerTripId, List.of(seat));

        adapter.releaseSeats(null, reservation.providerBlockReference());
        adapter.releaseSeats(null, reservation.providerBlockReference()); // no exception on repeat

        ProviderSeatMap afterRelease = adapter.getSeatMap(null, providerTripId);
        assertThat(afterRelease.seats().stream().filter(s -> s.seatNumber().equals(seat)).findFirst().orElseThrow()
                .isAvailable()).isTrue();
    }

    @Test
    void blockingAnAlreadyUnavailableSeatThrowsSeatUnavailable() {
        String providerTripId = adapter.search(null, criteria).get(0).providerTripId();
        ProviderSeatMap seatMap = adapter.getSeatMap(null, providerTripId);
        SeatNumber unavailableSeat = seatMap.seats().stream().filter(s -> !s.isAvailable()).findFirst().orElseThrow()
                .seatNumber();

        assertThatThrownBy(() -> adapter.blockSeats(null, providerTripId, List.of(unavailableSeat)))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void confirmingAnUnknownBlockReferenceThrowsBookingFailed() {
        String providerTripId = adapter.search(null, criteria).get(0).providerTripId();

        assertThatThrownBy(() -> adapter.confirmBooking(null, "MOCK-BLK-does-not-exist", providerTripId,
                List.of(new PassengerDetail("Jane Doe", 30, "F", new SeatNumber("L1")))))
                .isInstanceOf(BookingFailedException.class);
    }

    @Test
    void seatMapForAnUnknownTripThrowsTripNotFound() {
        assertThatThrownBy(() -> adapter.getSeatMap(null, "does-not-exist"))
                .isInstanceOf(ProviderTripNotFoundException.class);
    }

    @Test
    void ticketForAnUnknownBookingReferenceThrowsTicketNotFound() {
        assertThatThrownBy(() -> adapter.downloadTicket(null,
                new com.roadscanner.providerintegrationservice.domain.model.BookingReference("does-not-exist")))
                .isInstanceOf(TicketNotFoundException.class);
    }
}
