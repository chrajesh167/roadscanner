package com.roadscanner.bookingservice.application.usecase.cancellation;

import com.roadscanner.bookingservice.domain.exception.BookingNotFoundException;
import com.roadscanner.bookingservice.domain.exception.ProviderOrderNotReversibleException;
import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.port.in.CancelBooking;
import com.roadscanner.bookingservice.domain.port.out.BookingEventPublisher;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;
import com.roadscanner.bookingservice.domain.port.out.OperatorCancellationPolicyClient;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.bookingservice.domain.port.out.RefundRequestPort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Implements {@link CancelBooking} — the traveler-initiated half of
 * docs/services/booking-service/booking-state-machine.md's "Cancellation (Traveler-Initiated)".
 * If {@code PENDING_PAYMENT}: releases the hold, no refund needed. If {@code CONFIRMED}: checks
 * the (currently interim, full-refund-by-default) cancellation policy, cancels the order with the
 * provider that issued it, and only then requests a refund — closing
 * docs/services/booking-service/boundaries.md's "Known Gap: Post-Confirmation Cancellation".
 * Already-terminal bookings are a no-op (idempotent).
 */
public class CancelBookingService implements CancelBooking {

    private final BookingRepository bookingRepository;
    private final ProviderIntegrationClient providerIntegrationClient;
    private final OperatorCancellationPolicyClient policyClient;
    private final RefundRequestPort refundRequestPort;
    private final BookingEventPublisher eventPublisher;
    private final Clock clock;

    public CancelBookingService(BookingRepository bookingRepository,
                                 ProviderIntegrationClient providerIntegrationClient,
                                 OperatorCancellationPolicyClient policyClient, RefundRequestPort refundRequestPort,
                                 BookingEventPublisher eventPublisher, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.providerIntegrationClient = providerIntegrationClient;
        this.policyClient = policyClient;
        this.refundRequestPort = refundRequestPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public Result cancel(Command command) {
        Booking booking = bookingRepository.findById(command.bookingId())
                .filter(b -> canCancel(b, command.requester()))
                .orElseThrow(() -> new BookingNotFoundException(command.bookingId()));

        Instant now = clock.instant();
        BookingStatus previousStatus = booking.status();

        if (previousStatus == BookingStatus.PENDING_PAYMENT) {
            providerIntegrationClient.releaseSeat(booking.providerType(), booking.providerBlockReference());
            booking.cancel(CancellationReason.TRAVELER_REQUESTED, now);
            bookingRepository.save(booking);
            eventPublisher.publishBookingCancelled(booking, now);
        } else if (previousStatus == BookingStatus.CONFIRMED) {
            OperatorCancellationPolicyClient.CancellationPolicy policy =
                    policyClient.getCancellationPolicy(booking.tripId());

            // The provider is reversed first, and a failure here aborts the whole cancellation.
            // Refunding a traveller for a seat the provider still considers sold is how
            // RoadScanner ends up paying for a journey nobody takes.
            cancelWithProvider(booking);

            booking.cancel(CancellationReason.TRAVELER_REQUESTED, now);
            bookingRepository.save(booking);
            BigDecimal refundAmount = policy.fullRefundEligible() ? null : policy.feeAmount();
            booking.paymentReference()
                    .ifPresent(ref -> refundRequestPort.requestRefund(booking.id(), ref, refundAmount));
            eventPublisher.publishBookingCancelled(booking, now);
        }
        // else: already CANCELLED or COMPLETED — idempotent no-op, no side effects, no event.

        return new Result(booking.status());
    }

    /**
     * Reverses the confirmed order with the provider that issued it.
     *
     * <p>Keyed by the <strong>order</strong> reference, which is what the provider's cancel route
     * accepts. This previously sent the <em>booking</em> reference — a different identifier the
     * provider issues alongside it — so every explicit cancellation of a confirmed booking was
     * rejected as an unknown order and surfaced to the traveller as a 503.
     *
     * <p>A booking with no provider booking reference never reached the provider, so there is
     * nothing to reverse and cancellation continues. One that reached the provider but has no
     * recorded order reference is refused instead: proceeding would refund the traveller while
     * leaving a live, paid order at the provider, which is the failure this whole step exists to
     * prevent. Anything the provider itself raises propagates for the same reason.
     */
    private void cancelWithProvider(Booking booking) {
        if (booking.providerBookingReference().isEmpty()) {
            return;
        }
        String orderReference = booking.providerOrderReference()
                .orElseThrow(() -> new ProviderOrderNotReversibleException(booking.id()));
        providerIntegrationClient.cancelBooking(booking.providerType(), orderReference,
                "cancelled by traveller request");
    }

    private boolean canCancel(Booking booking, RequesterContext requester) {
        return switch (requester.role()) {
            case ADMIN, SUPPORT -> true;
            case TRAVELER -> booking.isOwnedBy(requester.requesterId());
            case OPERATOR -> false;
        };
    }
}
