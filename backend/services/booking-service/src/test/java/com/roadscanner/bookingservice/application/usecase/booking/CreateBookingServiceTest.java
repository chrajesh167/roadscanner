package com.roadscanner.bookingservice.application.usecase.booking;

import com.roadscanner.bookingservice.domain.exception.PassengerSeatMismatchException;
import com.roadscanner.bookingservice.domain.exception.SeatHoldExpiredException;
import com.roadscanner.bookingservice.domain.exception.SeatHoldNotFoundException;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.SeatHold;
import com.roadscanner.bookingservice.domain.model.SeatHoldId;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.CreateBooking;
import com.roadscanner.bookingservice.testsupport.MutableClock;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateBookingServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemorySeatHoldRepository seatHoldRepository = new InMemorySeatHoldRepository();
    private final InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    private final RecordingBookingEventPublisher eventPublisher = new RecordingBookingEventPublisher();
    private final MutableClock clock = new MutableClock(T0);
    private final CreateBookingService service =
            new CreateBookingService(seatHoldRepository, bookingRepository, eventPublisher, clock);

    private SeatHold heldSeat(UUID travelerId, Instant expiresAt) {
        SeatHold hold = SeatHold.create(SeatHoldId.generate(), travelerId, new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", List.of("L1"),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), expiresAt, T0);
        seatHoldRepository.save(hold);
        return hold;
    }

    @Test
    void createsABookingAndConsumesTheHold() {
        UUID travelerId = UUID.randomUUID();
        SeatHold hold = heldSeat(travelerId, T0.plusSeconds(600));

        CreateBooking.Result result = service.create(new CreateBooking.Command(travelerId, hold.id(),
                List.of(new CreateBooking.PassengerInput("Jane Doe", 30, "F", "L1"))));

        assertThat(result.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(bookingRepository.findById(result.bookingId())).isPresent();
        assertThat(seatHoldRepository.findById(hold.id())).isEmpty();
        assertThat(eventPublisher.events()).hasSize(1);
        assertThat(eventPublisher.events().get(0).eventType()).isEqualTo("BookingCreated");
    }

    @Test
    void failsWhenHoldExpired() {
        UUID travelerId = UUID.randomUUID();
        SeatHold hold = heldSeat(travelerId, T0.plusSeconds(600));
        clock.advanceBy(java.time.Duration.ofSeconds(700));

        assertThatThrownBy(() -> service.create(new CreateBooking.Command(travelerId, hold.id(),
                List.of(new CreateBooking.PassengerInput("Jane Doe", 30, "F", "L1")))))
                .isInstanceOf(SeatHoldExpiredException.class);
    }

    @Test
    void failsForAHoldOwnedBySomeoneElse() {
        SeatHold hold = heldSeat(UUID.randomUUID(), T0.plusSeconds(600));

        assertThatThrownBy(() -> service.create(new CreateBooking.Command(UUID.randomUUID(), hold.id(),
                List.of(new CreateBooking.PassengerInput("Jane Doe", 30, "F", "L1")))))
                .isInstanceOf(SeatHoldNotFoundException.class);
    }

    @Test
    void failsWhenPassengerSeatsDoNotMatchHeldSeats() {
        UUID travelerId = UUID.randomUUID();
        SeatHold hold = heldSeat(travelerId, T0.plusSeconds(600));

        assertThatThrownBy(() -> service.create(new CreateBooking.Command(travelerId, hold.id(),
                List.of(new CreateBooking.PassengerInput("Jane Doe", 30, "F", "L2")))))
                .isInstanceOf(PassengerSeatMismatchException.class);
    }

    @Test
    void aConsumedHoldCannotBecomeASecondBooking() {
        UUID travelerId = UUID.randomUUID();
        SeatHold hold = heldSeat(travelerId, T0.plusSeconds(600));
        service.create(new CreateBooking.Command(travelerId, hold.id(),
                List.of(new CreateBooking.PassengerInput("Jane Doe", 30, "F", "L1"))));

        assertThatThrownBy(() -> service.create(new CreateBooking.Command(travelerId, hold.id(),
                List.of(new CreateBooking.PassengerInput("Jane Doe", 30, "F", "L1")))))
                .isInstanceOf(SeatHoldNotFoundException.class);
    }
}
