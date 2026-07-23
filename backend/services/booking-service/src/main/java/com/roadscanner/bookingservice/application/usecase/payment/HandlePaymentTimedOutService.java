package com.roadscanner.bookingservice.application.usecase.payment;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentTimedOut;
import com.roadscanner.bookingservice.domain.port.out.BookingEventPublisher;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implements {@link HandlePaymentTimedOut} — same effect as {@code HandlePaymentFailedService},
 * distinct reason preserved for reconciliation (docs/architecture/payment-flow.md). */
public class HandlePaymentTimedOutService implements HandlePaymentTimedOut {

    private static final Logger log = LoggerFactory.getLogger(HandlePaymentTimedOutService.class);

    private final BookingRepository bookingRepository;
    private final ProviderIntegrationClient providerIntegrationClient;
    private final BookingEventPublisher eventPublisher;

    public HandlePaymentTimedOutService(BookingRepository bookingRepository,
                                         ProviderIntegrationClient providerIntegrationClient,
                                         BookingEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.providerIntegrationClient = providerIntegrationClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void handle(Command command) {
        Booking booking = bookingRepository.findById(command.bookingId()).orElse(null);
        if (booking == null) {
            log.warn("PaymentTimedOut for unknown booking {} — ignoring", command.bookingId());
            return;
        }
        if (booking.status() != BookingStatus.PENDING_PAYMENT) {
            return; // idempotent no-op
        }
        providerIntegrationClient.releaseSeat(booking.providerType(), booking.providerBlockReference());
        booking.cancel(CancellationReason.PAYMENT_TIMED_OUT, command.occurredAt());
        bookingRepository.save(booking);
        eventPublisher.publishBookingCancelled(booking, command.occurredAt());
    }
}
