package com.roadscanner.bookingservice.application.usecase.cancellation;

import com.roadscanner.bookingservice.domain.exception.BookingNotFoundException;
import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.model.Contact;
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
import java.time.LocalDate;
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
                List.of(new Passenger("Jane", "Doe", LocalDate.of(1994, 3, 17), "F", "L1")),
                new Contact("+919876543210", "traveller@example.com", Contact.CommunicationPreference.EMAIL),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
        bookingRepository.save(booking);
        return booking;
    }

    /** The provider issues two distinct identifiers at confirmation, and only one of them is
     * accepted by its cancel route. Naming them apart here is what makes a test able to tell them
     * apart at all — with a single value, sending the wrong one looks correct. */
    private static final String BOOKING_REFERENCE = "MOCK-BK-1";
    private static final String ORDER_REFERENCE = "MOCK-ORD-1";

    private Booking confirmedBooking(UUID travelerId) {
        Booking booking = pendingPaymentBooking(travelerId);
        booking.confirm(BOOKING_REFERENCE, ORDER_REFERENCE,
                new Ticket("t1", "PDF", "c".getBytes(), T0), T0.plusSeconds(10));
        booking.associatePaymentReference("payment-ref-1");
        bookingRepository.save(booking);
        return booking;
    }

    /** A booking confirmed before V3 recorded order references: reached the provider, but this
     * service cannot name the order. */
    private Booking legacyConfirmedBookingWithNoOrderReference(UUID travelerId) {
        Booking booking = pendingPaymentBooking(travelerId);
        booking.confirm(BOOKING_REFERENCE, null, new Ticket("t1", "PDF", "c".getBytes(), T0), T0.plusSeconds(10));
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
    void cancellingAConfirmedBookingReversesTheProviderOrderAndThenRefunds() {
        UUID travelerId = UUID.randomUUID();
        Booking booking = confirmedBooking(travelerId);

        CancelBooking.Result result = service.cancel(
                new CancelBooking.Command(booking.id(), new RequesterContext(travelerId, Role.TRAVELER)));

        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        // The seat block is long spent on a confirmed booking; it is the *order* that must be
        // reversed. Refunding without this left the provider holding a live, paid order.
        assertThat(providerIntegrationClient.releaseSeatCallCount).isEqualTo(0);
        assertThat(providerIntegrationClient.cancelBookingCallCount).isEqualTo(1);
        // The *order* reference, not the booking reference. Sending the latter is what made every
        // confirmed cancellation fail: the provider rejected it as an unknown order.
        assertThat(providerIntegrationClient.cancelledOrderReference).isEqualTo(ORDER_REFERENCE);
        assertThat(providerIntegrationClient.cancelledOrderReference)
                .isNotEqualTo(booking.providerBookingReference().orElseThrow());
        assertThat(refundRequestPort.requests()).hasSize(1);
        assertThat(refundRequestPort.requests().get(0).paymentReference()).isEqualTo("payment-ref-1");
    }

    @Test
    void aFailedProviderReversalAbortsTheCancellationRatherThanRefundingAnyway() {
        UUID travelerId = UUID.randomUUID();
        Booking booking = confirmedBooking(travelerId);
        providerIntegrationClient.cancelBookingResult = () -> {
            throw new com.roadscanner.bookingservice.domain.exception.UpstreamServiceUnavailableException(
                    "provider-integration-service", "cancel unavailable");
        };

        assertThatThrownBy(() -> service.cancel(
                new CancelBooking.Command(booking.id(), new RequesterContext(travelerId, Role.TRAVELER))))
                .isInstanceOf(com.roadscanner.bookingservice.domain.exception.UpstreamServiceUnavailableException.class);

        // Nothing may move if the provider still holds the order: a CANCELLED booking plus a
        // refund, against a seat the provider considers sold, is money RoadScanner never recovers.
        assertThat(bookingRepository.findById(booking.id()).orElseThrow().status())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(refundRequestPort.requests()).isEmpty();
        assertThat(eventPublisher.events()).isEmpty();
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

    @Test
    void aConfirmedBookingWithNoRecordedOrderReferenceIsRefusedRatherThanRefundedBlind() {
        UUID travelerId = UUID.randomUUID();
        Booking booking = legacyConfirmedBookingWithNoOrderReference(travelerId);

        assertThatThrownBy(() -> service.cancel(
                new CancelBooking.Command(booking.id(), new RequesterContext(travelerId, Role.TRAVELER))))
                .isInstanceOf(com.roadscanner.bookingservice.domain.exception.ProviderOrderNotReversibleException.class);

        // The order is live at the provider and unnameable here. Cancelling locally and refunding
        // anyway would pay for a seat the provider still considers sold — the exact failure the
        // provider-first ordering exists to prevent.
        assertThat(providerIntegrationClient.cancelBookingCallCount).isEqualTo(0);
        assertThat(bookingRepository.findById(booking.id()).orElseThrow().status())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(refundRequestPort.requests()).isEmpty();
        assertThat(eventPublisher.events()).isEmpty();
    }

    @Test
    void aSuccessfulConfirmedCancellationPublishesExactlyOneCancellationEvent() {
        UUID travelerId = UUID.randomUUID();
        Booking booking = confirmedBooking(travelerId);

        service.cancel(new CancelBooking.Command(booking.id(), new RequesterContext(travelerId, Role.TRAVELER)));

        // notification-service consumes this, and sends on TRAVELER_REQUESTED. A second event would
        // become a second message to the customer.
        assertThat(eventPublisher.events()).hasSize(1);
    }

    @Test
    void cancellingTwiceReversesTheProviderOnceAndPublishesOnce() {
        UUID travelerId = UUID.randomUUID();
        Booking booking = confirmedBooking(travelerId);
        CancelBooking.Command command =
                new CancelBooking.Command(booking.id(), new RequesterContext(travelerId, Role.TRAVELER));

        service.cancel(command);
        service.cancel(command);

        // The second call is a no-op on a terminal booking: no second provider reversal, no second
        // refund, and — the reason this matters downstream — no second cancellation event, so
        // notification-service never has a duplicate to de-duplicate.
        assertThat(providerIntegrationClient.cancelBookingCallCount).isEqualTo(1);
        assertThat(refundRequestPort.requests()).hasSize(1);
        assertThat(eventPublisher.events()).hasSize(1);
    }
}
