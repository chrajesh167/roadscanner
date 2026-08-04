package com.roadscanner.providerintegrationservice.adapter.out.provider.mock;

import com.roadscanner.providerintegrationservice.domain.exception.BookingFailedException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderTripNotFoundException;
import com.roadscanner.providerintegrationservice.domain.exception.SeatUnavailableException;
import com.roadscanner.providerintegrationservice.domain.exception.TicketNotFoundException;
import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.ContactDetail;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.ReservationId;
import com.roadscanner.providerintegrationservice.domain.model.SeatAssignment;
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

    private static final com.roadscanner.providerintegrationservice.domain.model.Provider PROVIDER =
            com.roadscanner.providerintegrationservice.domain.model.Provider.reconstitute(
                    com.roadscanner.providerintegrationservice.domain.model.ProviderId.generate(),
                    com.roadscanner.providerintegrationservice.domain.model.ProviderType.MOCK,
                    com.roadscanner.providerintegrationservice.domain.model.ProviderCategory.BUS,
                    "Mock", true, java.util.Set.of(), null, 5_000, 1,
                    java.time.Instant.parse("2026-07-01T00:00:00Z"),
                    java.time.Instant.parse("2026-07-01T00:00:00Z"));

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);

    private final MockProviderClientAdapter adapter = new MockProviderClientAdapter(CLOCK);
    private final SearchCriteria criteria = new SearchCriteria("Mumbai", "Pune", LocalDate.of(2026, 8, 1));

    private static PassengerDetail passenger(SeatNumber seat) {
        return new PassengerDetail("Jane", "Doe", java.time.LocalDate.of(1994, 3, 17),
                PassengerDetail.Gender.FEMALE, seat);
    }

    private static ContactDetail contact() {
        return new ContactDetail("+919876543210", "jane@example.com",
                ContactDetail.CommunicationPreference.EMAIL);
    }

    @Test
    void fullHappyPathFromSearchToTicket() {
        List<ProviderTrip> trips = adapter.search(null, criteria);
        assertThat(trips).isNotEmpty();
        String providerTripId = trips.get(0).providerTripId();

        ProviderSeatMap seatMap = adapter.getSeatMap(null, providerTripId);
        SeatNumber availableSeat = seatMap.seats().stream().filter(ProviderSeat::isAvailable).findFirst()
                .orElseThrow().seatNumber();

        List<PassengerDetail> passengers = List.of(passenger(availableSeat));
        SeatReservation reservation = adapter.blockSeats(null, providerTripId, passengers);
        assertThat(reservation.seatNumbers()).containsExactly(availableSeat);

        BookingConfirmation confirmation = adapter.confirmBooking(null, reservation, contact(), passengers);
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
        SeatReservation reservation = adapter.blockSeats(null, providerTripId, List.of(passenger(seat)));

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

        assertThatThrownBy(() -> adapter.blockSeats(null, providerTripId, List.of(passenger(unavailableSeat))))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void confirmingAnUnknownBlockReferenceThrowsBookingFailed() {
        String providerTripId = adapter.search(null, criteria).get(0).providerTripId();

        SeatReservation unknown = SeatReservation.block(ReservationId.generate(), ProviderType.MOCK,
                "MOCK-BLK-does-not-exist", providerTripId,
                List.of(new SeatAssignment(new SeatNumber("L1"), "seat-x", "ticket-x")),
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> adapter.confirmBooking(null, unknown, contact(),
                List.of(passenger(new SeatNumber("L1")))))
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
