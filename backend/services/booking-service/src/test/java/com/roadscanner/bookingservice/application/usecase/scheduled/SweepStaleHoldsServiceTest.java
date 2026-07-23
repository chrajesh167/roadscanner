package com.roadscanner.bookingservice.application.usecase.scheduled;

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
import com.roadscanner.bookingservice.domain.port.in.SweepStaleHolds;
import com.roadscanner.bookingservice.testsupport.MutableClock;
import com.roadscanner.bookingservice.testsupport.fakes.InMemoryBookingRepository;
import com.roadscanner.bookingservice.testsupport.fakes.InMemorySeatHoldRepository;
import com.roadscanner.bookingservice.testsupport.fakes.RecordingBookingEventPublisher;
import com.roadscanner.bookingservice.testsupport.fakes.StubProviderIntegrationClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SweepStaleHoldsServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemorySeatHoldRepository seatHoldRepository = new InMemorySeatHoldRepository();
    private final InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    private final StubProviderIntegrationClient providerIntegrationClient = new StubProviderIntegrationClient();
    private final RecordingBookingEventPublisher eventPublisher = new RecordingBookingEventPublisher();
    private final MutableClock clock = new MutableClock(T0);
    private final SweepStaleHoldsService service = new SweepStaleHoldsService(seatHoldRepository, bookingRepository,
            providerIntegrationClient, eventPublisher, clock);

    @Test
    void removesExpiredHoldsAndCancelsStalePendingPaymentBookings() {
        SeatHold expiredHold = SeatHold.create(SeatHoldId.generate(), UUID.randomUUID(),
                new TripId(UUID.randomUUID()), T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1",
                "block-ref-1", List.of("L1"), new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")),
                T0.minusSeconds(100), T0.minusSeconds(700));
        seatHoldRepository.save(expiredHold);

        SeatHold freshHold = SeatHold.create(SeatHoldId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-2", List.of("L2"),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0.plusSeconds(600), T0);
        seatHoldRepository.save(freshHold);

        Booking staleBooking = Booking.create(BookingId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-3", T0.minusSeconds(100),
                List.of(new Passenger("A", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0.minusSeconds(700));
        bookingRepository.save(staleBooking);

        SweepStaleHolds.Result result = service.sweep();

        assertThat(result.expiredHoldsRemoved()).isEqualTo(1);
        assertThat(result.bookingsCancelled()).isEqualTo(1);
        assertThat(seatHoldRepository.findById(expiredHold.id())).isEmpty();
        assertThat(seatHoldRepository.findById(freshHold.id())).isPresent();
        assertThat(bookingRepository.findById(staleBooking.id()).get().status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(staleBooking.id()).get().cancellationReason())
                .contains(CancellationReason.HOLD_EXPIRED);
        assertThat(eventPublisher.events()).hasSize(1);
    }
}
