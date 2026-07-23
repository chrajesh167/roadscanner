package com.roadscanner.bookingservice.application.usecase.payment;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentFailed;
import com.roadscanner.bookingservice.testsupport.fakes.InMemoryBookingRepository;
import com.roadscanner.bookingservice.testsupport.fakes.RecordingBookingEventPublisher;
import com.roadscanner.bookingservice.testsupport.fakes.StubProviderIntegrationClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HandlePaymentFailedServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    private final StubProviderIntegrationClient providerIntegrationClient = new StubProviderIntegrationClient();
    private final RecordingBookingEventPublisher eventPublisher = new RecordingBookingEventPublisher();
    private final HandlePaymentFailedService service =
            new HandlePaymentFailedService(bookingRepository, providerIntegrationClient, eventPublisher);

    @Test
    void cancelsAndReleasesTheHoldWithoutARefund() {
        Booking booking = Booking.create(BookingId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        bookingRepository.save(booking);

        service.handle(new HandlePaymentFailed.Command(booking.id(), T0.plusSeconds(30)));

        Booking updated = bookingRepository.findById(booking.id()).orElseThrow();
        assertThat(updated.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(updated.cancellationReason()).contains(CancellationReason.PAYMENT_FAILED);
        assertThat(providerIntegrationClient.releaseSeatCallCount).isEqualTo(1);
        assertThat(eventPublisher.events()).hasSize(1);
    }

    @Test
    void isIdempotentForAnAlreadyCancelledBooking() {
        Booking booking = Booking.create(BookingId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        booking.cancel(CancellationReason.TRAVELER_REQUESTED, T0.plusSeconds(10));
        bookingRepository.save(booking);

        service.handle(new HandlePaymentFailed.Command(booking.id(), T0.plusSeconds(30)));

        assertThat(providerIntegrationClient.releaseSeatCallCount).isEqualTo(0);
        assertThat(eventPublisher.events()).isEmpty();
    }
}
