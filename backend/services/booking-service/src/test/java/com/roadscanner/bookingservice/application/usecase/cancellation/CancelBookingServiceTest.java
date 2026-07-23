package com.roadscanner.bookingservice.application.usecase.cancellation;

import com.roadscanner.bookingservice.domain.exception.BookingNotFoundException;
import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.model.Fare;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.model.Role;
import com.roadscanner.bookingservice.domain.model.Ticket;
import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.in.CancelBooking;
import com.roadscanner.bookingservice.testsupport.MutableClock;
import com.roadscanner.bookingservice.testsupport.fakes.InMemoryBookingRepository;
import com.roadscanner.bookingservice.testsupport.fakes.RecordingBookingEventPublisher;
import com.roadscanner.bookingservice.testsupport.fakes.RecordingRefundRequestPort;
import com.roadscanner.bookingservice.testsupport.fakes.StubOperatorCancellationPolicyClient;
import com.roadscanner.bookingservice.testsupport.fakes.StubProviderIntegrationClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelBookingServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private final InMemoryBookingRepository bookingRepository = new InMemoryBookingRepository();
    private final StubProviderIntegrationClient providerIntegrationClient = new StubProviderIntegrationClient();
    private final StubOperatorCancellationPolicyClient policyClient = new StubOperatorCancellationPolicyClient();
    private final RecordingRefundRequestPort refundRequestPort = new RecordingRefundRequestPort();
    private final RecordingBookingEventPublisher eventPublisher = new RecordingBookingEventPublisher();
    private final MutableClock clock = new MutableClock(T0);
    private final CancelBookingService service = new CancelBookingService(bookingRepository,
            providerIntegrationClient, policyClient, refundRequestPort, eventPublisher, clock);

    private Booking pendingPaymentBooking(UUID travelerId) {
        Booking booking = Booking.create(BookingId.generate(), travelerId, new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        bookingRepository.save(booking);
        return booking;
    }

    private Booking confirmedBooking(UUID travelerId) {
        Booking booking = pendingPaymentBooking(travelerId);
        booking.confirm("provider-ref-1", new Ticket("t1", "PDF", "c".getBytes(), T0), T0.plusSeconds(10));
        booking.associatePaymentReference("payment-ref-1");
        bookingRepository.save(booking);
        return booking;
    }

    @Test
    void cancellingAPendingPaymentBookingReleasesTheHoldAndNeedsNoRefund() {
        UUID travelerId = UUID.randomUUID();
        Booking booking = pendingPaymentBooking(travelerId);

        CancelBooking.Result result = service.cancel(
                new CancelBooking.Command(booking.id(), new RequesterContext(travelerId, Role.TRAVELER)));

        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(providerIntegrationClient.releaseSeatCallCount).isEqualTo(1);
        assertThat(refundRequestPort.requests()).isEmpty();
        assertThat(bookingRepository.findById(booking.id()).get().cancellationReason())
                .contains(CancellationReason.TRAVELER_REQUESTED);
    }

    @Test
    void cancellingAConfirmedBookingRequestsARefundAndAttemptsNoProviderReversal() {
        UUID travelerId = UUID.randomUUID();
        Booking booking = confirmedBooking(travelerId);

        CancelBooking.Result result = service.cancel(
                new CancelBooking.Command(booking.id(), new RequesterContext(travelerId, Role.TRAVELER)));

        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(providerIntegrationClient.releaseSeatCallCount).isEqualTo(0);
        assertThat(refundRequestPort.requests()).hasSize(1);
        assertThat(refundRequestPort.requests().get(0).paymentReference()).isEqualTo("payment-ref-1");
    }

    @Test
    void cancellingAnAlreadyCancelledBookingIsIdempotentNoOp() {
        UUID travelerId = UUID.randomUUID();
        Booking booking = pendingPaymentBooking(travelerId);
        service.cancel(new CancelBooking.Command(booking.id(), new RequesterContext(travelerId, Role.TRAVELER)));

        CancelBooking.Result result = service.cancel(
                new CancelBooking.Command(booking.id(), new RequesterContext(travelerId, Role.TRAVELER)));

        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(providerIntegrationClient.releaseSeatCallCount).isEqualTo(1); // not called again
        assertThat(eventPublisher.events()).hasSize(1); // not published again
    }

    @Test
    void anotherTravelerCannotCancelSomeoneElsesBooking() {
        Booking booking = pendingPaymentBooking(UUID.randomUUID());

        assertThatThrownBy(() -> service.cancel(
                new CancelBooking.Command(booking.id(), new RequesterContext(UUID.randomUUID(), Role.TRAVELER))))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void adminCanCancelOnBehalfOfATraveler() {
        Booking booking = pendingPaymentBooking(UUID.randomUUID());

        CancelBooking.Result result = service.cancel(
                new CancelBooking.Command(booking.id(), new RequesterContext(UUID.randomUUID(), Role.ADMIN)));

        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
    }
}
