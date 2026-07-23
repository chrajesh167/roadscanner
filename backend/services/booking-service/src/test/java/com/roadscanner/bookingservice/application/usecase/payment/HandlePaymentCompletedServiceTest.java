package com.roadscanner.bookingservice.application.usecase.payment;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentCompleted;
import com.roadscanner.bookingservice.testsupport.fakes.InMemoryBookingRepository;
import com.roadscanner.bookingservice.testsupport.fakes.RecordingBookingEventPublisher;
import com.roadscanner.bookingservice.testsupport.fakes.RecordingRefundRequestPort;
import com.roadscanner.bookingservice.testsupport.fakes.StubProviderIntegrationClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HandlePaymentCompletedServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    private final StubProviderIntegrationClient providerIntegrationClient = new StubProviderIntegrationClient();
    private final RecordingRefundRequestPort refundRequestPort = new RecordingRefundRequestPort();
    private final RecordingBookingEventPublisher eventPublisher = new RecordingBookingEventPublisher();
    private final HandlePaymentCompletedService service = new HandlePaymentCompletedService(bookingRepository,
            providerIntegrationClient, refundRequestPort, eventPublisher);

    private Booking pendingPaymentBooking() {
        Booking booking = Booking.create(BookingId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        bookingRepository.save(booking);
        return booking;
    }

    @Test
    void confirmsTheBookingAndPersistsTheTicketOnSuccess() {
        Booking booking = pendingPaymentBooking();

        service.handle(new HandlePaymentCompleted.Command(booking.id(), "payment-ref-1", T0.plusSeconds(30)));

        Booking updated = bookingRepository.findById(booking.id()).orElseThrow();
        assertThat(updated.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(updated.ticket()).isPresent();
        assertThat(updated.providerBookingReference()).isPresent();
        assertThat(eventPublisher.events()).hasSize(1);
        assertThat(eventPublisher.events().get(0).eventType()).isEqualTo("BookingConfirmed");
    }

    @Test
    void cancelsAndRefundsWhenProviderConfirmationFails() {
        Booking booking = pendingPaymentBooking();
        providerIntegrationClient.confirmBookingResult = () -> {
            throw new com.roadscanner.bookingservice.domain.exception.SeatUnavailableException("provider declined");
        };

        service.handle(new HandlePaymentCompleted.Command(booking.id(), "payment-ref-1", T0.plusSeconds(30)));

        Booking updated = bookingRepository.findById(booking.id()).orElseThrow();
        assertThat(updated.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(updated.cancellationReason()).contains(CancellationReason.PROVIDER_CONFIRMATION_FAILED);
        assertThat(updated.supportFlagged()).isTrue();
        assertThat(refundRequestPort.requests()).hasSize(1);
        assertThat(eventPublisher.events().get(0).eventType()).isEqualTo("BookingCancelled");
    }

    @Test
    void isIdempotentForAnAlreadyConfirmedBooking() {
        Booking booking = pendingPaymentBooking();
        service.handle(new HandlePaymentCompleted.Command(booking.id(), "payment-ref-1", T0.plusSeconds(30)));

        service.handle(new HandlePaymentCompleted.Command(booking.id(), "payment-ref-1", T0.plusSeconds(60)));

        assertThat(providerIntegrationClient.confirmBookingCallCount).isEqualTo(1);
        assertThat(eventPublisher.events()).hasSize(1);
    }

    @Test
    void lateSuccessAfterCancellationRefundsAndFlagsButNeverReconfirms() {
        Booking booking = pendingPaymentBooking();
        booking.cancel(CancellationReason.PAYMENT_TIMED_OUT, T0.plusSeconds(20));
        bookingRepository.save(booking);

        service.handle(new HandlePaymentCompleted.Command(booking.id(), "payment-ref-1", T0.plusSeconds(30)));

        Booking updated = bookingRepository.findById(booking.id()).orElseThrow();
        assertThat(updated.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(updated.supportFlagged()).isTrue();
        assertThat(refundRequestPort.requests()).hasSize(1);
        assertThat(providerIntegrationClient.confirmBookingCallCount).isEqualTo(0);
    }
}
