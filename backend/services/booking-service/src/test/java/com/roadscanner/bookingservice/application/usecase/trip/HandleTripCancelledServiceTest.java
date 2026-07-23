package com.roadscanner.bookingservice.application.usecase.trip;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.Ticket;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.HandleTripCancelled;
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

class HandleTripCancelledServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    private final StubProviderIntegrationClient providerIntegrationClient = new StubProviderIntegrationClient();
    private final RecordingRefundRequestPort refundRequestPort = new RecordingRefundRequestPort();
    private final RecordingBookingEventPublisher eventPublisher = new RecordingBookingEventPublisher();
    private final HandleTripCancelledService service = new HandleTripCancelledService(bookingRepository,
            providerIntegrationClient, refundRequestPort, eventPublisher);

    @Test
    void cascadesCancellationToEveryNonTerminalBookingForTheTrip() {
        TripId tripId = new TripId(UUID.randomUUID());

        Booking pending = Booking.create(BookingId.generate(), UUID.randomUUID(), tripId, T0.plusSeconds(3600),
                new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("A", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        bookingRepository.save(pending);

        Booking confirmed = Booking.create(BookingId.generate(), UUID.randomUUID(), tripId, T0.plusSeconds(3600),
                new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-2", T0.plusSeconds(600),
                List.of(new Passenger("B", 30, "F", "L2")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        confirmed.confirm("ref", new Ticket("t", "PDF", "c".getBytes(), T0), T0.plusSeconds(10));
        confirmed.associatePaymentReference("payment-ref-1");
        bookingRepository.save(confirmed);

        // A booking on a different trip must not be touched.
        Booking otherTrip = Booking.create(BookingId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-3", T0.plusSeconds(600),
                List.of(new Passenger("C", 30, "F", "L3")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        bookingRepository.save(otherTrip);

        service.handle(new HandleTripCancelled.Command(tripId, T0.plusSeconds(1000)));

        assertThat(bookingRepository.findById(pending.id()).get().status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(pending.id()).get().cancellationReason())
                .contains(CancellationReason.TRIP_CANCELLED);
        assertThat(bookingRepository.findById(confirmed.id()).get().status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(otherTrip.id()).get().status()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        assertThat(providerIntegrationClient.releaseSeatCallCount).isEqualTo(1); // only the pending one
        assertThat(refundRequestPort.requests()).hasSize(1); // only the confirmed one, full refund
        assertThat(refundRequestPort.requests().get(0).amount()).isNull();
        assertThat(eventPublisher.events()).hasSize(2);
    }
}
