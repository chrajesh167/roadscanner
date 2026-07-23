package com.roadscanner.bookingservice.application.usecase.verification;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.Ticket;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.VerifyBooking;
import com.roadscanner.bookingservice.testsupport.fakes.InMemoryBookingRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VerifyBookingServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    private final VerifyBookingService service = new VerifyBookingService(bookingRepository);

    @Test
    void trueOnlyForACompletedBookingByThatTravelerForThatTrip() {
        UUID travelerId = UUID.randomUUID();
        TripId tripId = new TripId(UUID.randomUUID());
        Booking booking = Booking.create(BookingId.generate(), travelerId, tripId, T0.plusSeconds(3600),
                new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        booking.confirm("ref", new Ticket("t", "PDF", "c".getBytes(), T0), T0.plusSeconds(10));
        booking.complete(T0.plusSeconds(9999));
        bookingRepository.save(booking);

        assertThat(service.verify(new VerifyBooking.Command(travelerId, tripId)).verified()).isTrue();
        assertThat(service.verify(new VerifyBooking.Command(UUID.randomUUID(), tripId)).verified()).isFalse();
        assertThat(service.verify(new VerifyBooking.Command(travelerId, new TripId(UUID.randomUUID()))).verified())
                .isFalse();
    }

    @Test
    void falseForAConfirmedButNotYetCompletedBooking() {
        UUID travelerId = UUID.randomUUID();
        TripId tripId = new TripId(UUID.randomUUID());
        Booking booking = Booking.create(BookingId.generate(), travelerId, tripId, T0.plusSeconds(3600),
                new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        booking.confirm("ref", new Ticket("t", "PDF", "c".getBytes(), T0), T0.plusSeconds(10));
        bookingRepository.save(booking);

        assertThat(service.verify(new VerifyBooking.Command(travelerId, tripId)).verified()).isFalse();
    }
}
