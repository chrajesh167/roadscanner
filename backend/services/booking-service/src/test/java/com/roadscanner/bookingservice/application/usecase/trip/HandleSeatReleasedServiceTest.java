package com.roadscanner.bookingservice.application.usecase.trip;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.SeatHold;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.HandleSeatReleased;
import com.roadscanner.bookingservice.testsupport.fakes.InMemoryBookingRepository;
import com.roadscanner.bookingservice.testsupport.fakes.InMemorySeatHoldRepository;
import com.roadscanner.bookingservice.testsupport.fakes.RecordingBookingEventPublisher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HandleSeatReleasedServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemorySeatHoldRepository seatHoldRepository = new InMemorySeatHoldRepository();
    private final InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    private final RecordingBookingEventPublisher eventPublisher = new RecordingBookingEventPublisher();
    private final HandleSeatReleasedService service =
            new HandleSeatReleasedService(seatHoldRepository, bookingRepository, eventPublisher);

    @Test
    void discardsAnOutstandingHoldWithNoBookingYet() {
        SeatHold hold = SeatHold.create(SeatHoldId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", List.of("L1"),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0.plusSeconds(600), T0);
        seatHoldRepository.save(hold);

        service.handle(new HandleSeatReleased.Command("block-ref-1", T0.plusSeconds(700)));

        assertThat(seatHoldRepository.findById(hold.id())).isEmpty();
        assertThat(eventPublisher.events()).isEmpty();
    }

    @Test
    void cancelsAPendingPaymentBookingReferencingTheReleasedReservation() {
        Booking booking = Booking.create(BookingId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        bookingRepository.save(booking);

        service.handle(new HandleSeatReleased.Command("block-ref-1", T0.plusSeconds(700)));

        Booking updated = bookingRepository.findById(booking.id()).orElseThrow();
        assertThat(updated.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(updated.cancellationReason()).contains(CancellationReason.HOLD_EXPIRED);
        assertThat(eventPublisher.events()).hasSize(1);
    }

    @Test
    void isANoOpForAnAlreadyConfirmedBooking() {
        Booking booking = Booking.create(BookingId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        booking.confirm("ref", new com.roadscanner.bookingservice.domain.model.Ticket("t", "PDF", "c".getBytes(), T0),
                T0.plusSeconds(10));
        bookingRepository.save(booking);

        service.handle(new HandleSeatReleased.Command("block-ref-1", T0.plusSeconds(700)));

        assertThat(bookingRepository.findById(booking.id()).get().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(eventPublisher.events()).isEmpty();
    }
}
