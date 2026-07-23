package com.roadscanner.bookingservice.application.usecase.payment;

import com.roadscanner.bookingservice.domain.exception.BookingServiceException;
import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.model.Ticket;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentCompleted;
import com.roadscanner.bookingservice.domain.port.out.BookingEventPublisher;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.bookingservice.domain.port.out.RefundRequestPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements {@link HandlePaymentCompleted} — docs/architecture/booking-flow.md step 4,
 * including its flagged edge case (provider confirmation fails after payment already succeeded)
 * and docs/architecture/payment-flow.md's symmetric edge case (a late {@code PaymentCompleted}
 * arriving after this booking was already cancelled by a timeout). Both edge cases resolve to an
 * automatic refund plus {@code supportFlagged = true}, never a silent re-confirmation or a
 * silently kept payment.
 */
public class HandlePaymentCompletedService implements HandlePaymentCompleted {

    private static final Logger log = LoggerFactory.getLogger(HandlePaymentCompletedService.class);

    private final BookingRepository bookingRepository;
    private final ProviderIntegrationClient providerIntegrationClient;
    private final RefundRequestPort refundRequestPort;
    private final BookingEventPublisher eventPublisher;

    public HandlePaymentCompletedService(BookingRepository bookingRepository,
                                          ProviderIntegrationClient providerIntegrationClient,
                                          RefundRequestPort refundRequestPort, BookingEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.providerIntegrationClient = providerIntegrationClient;
        this.refundRequestPort = refundRequestPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void handle(Command command) {
        Booking booking = bookingRepository.findById(command.bookingId()).orElse(null);
        if (booking == null) {
            log.warn("PaymentCompleted for unknown booking {} — ignoring", command.bookingId());
            return;
        }
        booking.associatePaymentReference(command.paymentReference());

        if (booking.status() == BookingStatus.CONFIRMED) {
            bookingRepository.save(booking);
            return; // idempotent no-op — duplicate delivery for an already-confirmed booking
        }
        if (booking.status() != BookingStatus.PENDING_PAYMENT) {
            // Late success after a timeout-driven cancellation (docs/architecture/payment-flow.md).
            // The seat may already be gone — never silently re-confirm.
            handleLateSuccessAfterCancellation(booking, command);
            return;
        }

        try {
            ProviderIntegrationClient.BookingConfirmationView confirmation = providerIntegrationClient.confirmBooking(
                    booking.providerType(), booking.providerTripId(), booking.providerBlockReference(),
                    booking.passengers());
            ProviderIntegrationClient.TicketView ticketView = providerIntegrationClient.downloadTicket(
                    booking.providerType(), confirmation.providerBookingReference());
            Ticket ticket = new Ticket(ticketView.providerTicketId(), ticketView.format(), ticketView.content(),
                    ticketView.issuedAt());

            booking.confirm(confirmation.providerBookingReference(), ticket, command.occurredAt());
            bookingRepository.save(booking);
            eventPublisher.publishBookingConfirmed(booking, command.occurredAt());
        } catch (BookingServiceException e) {
            // Deliberately broad: SeatUnavailableException, UpstreamServiceUnavailableException,
            // or anything else this service raises during confirm/ticket-download all get the
            // same remediation — docs/architecture/booking-flow.md's flagged edge case treats a
            // late-arriving rejection, an expired block, and a provider outage identically.
            log.error("Provider confirmation failed after payment already succeeded for booking {}",
                    booking.id(), e);
            booking.cancel(CancellationReason.PROVIDER_CONFIRMATION_FAILED, command.occurredAt());
            booking.markSupportFlagged();
            bookingRepository.save(booking);
            booking.paymentReference()
                    .ifPresent(ref -> refundRequestPort.requestRefund(booking.id(), ref, null));
            eventPublisher.publishBookingCancelled(booking, command.occurredAt());
        }
    }

    private void handleLateSuccessAfterCancellation(Booking booking, Command command) {
        booking.markSupportFlagged();
        bookingRepository.save(booking);
        booking.paymentReference().ifPresent(ref -> refundRequestPort.requestRefund(booking.id(), ref, null));
    }
}
