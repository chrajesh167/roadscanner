package com.roadscanner.bookingservice.application.usecase.scheduled;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.Ticket;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.CompleteBooking;
import com.roadscanner.bookingservice.testsupport.MutableClock;
import com.roadscanner.bookingservice.testsupport.fakes.InMemoryBookingRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompleteBookingServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    private final MutableClock clock = new MutableClock(T0);
    private final CompleteBookingService service = new CompleteBookingService(bookingRepository, clock);

    private Booking confirmedBooking(Instant departureTime) {
        Booking booking = Booking.create(BookingId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                departureTime, new ProviderType("MOCK"), "MOCK-TRIP-1", UUID.randomUUID().toString(),
                departureTime.minusSeconds(1000), List.of(new Passenger("A", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        booking.confirm("ref", new Ticket("t", "PDF", "c".getBytes(), T0), T0.plusSeconds(10));
        bookingRepository.save(booking);
        return booking;
    }

    @Test
    void completesConfirmedBookingsWhoseDepartureHasPassed() {
        Booking departed = confirmedBooking(T0.minusSeconds(3600));
        Booking notYetDeparted = confirmedBooking(T0.plusSeconds(3600));

        CompleteBooking.Result result = service.completeDepartedTrips();

        assertThat(result.completedCount()).isEqualTo(1);
        assertThat(bookingRepository.findById(departed.id()).get().status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(bookingRepository.findById(notYetDeparted.id()).get().status()).isEqualTo(BookingStatus.CONFIRMED);
    }
}
