package com.roadscanner.bookingservice.application.usecase.payment;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.port.in.HandlePaymentFailed;
import com.roadscanner.bookingservice.domain.port.out.BookingEventPublisher;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implements {@link HandlePaymentFailed} — docs/architecture/booking-flow.md step 5. No refund
 * action; payment never succeeded. */
public class HandlePaymentFailedService implements HandlePaymentFailed {

    private static final Logger log = LoggerFactory.getLogger(HandlePaymentFailedService.class);

    private final BookingRepository bookingRepository;
    private final ProviderIntegrationClient providerIntegrationClient;
    private final BookingEventPublisher eventPublisher;

    public HandlePaymentFailedService(BookingRepository bookingRepository,
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
            log.warn("PaymentFailed for unknown booking {} — ignoring", command.bookingId());
            return;
        }
        if (booking.status() != BookingStatus.PENDING_PAYMENT) {
            return; // idempotent no-op
        }
        providerIntegrationClient.releaseSeat(booking.providerType(), booking.providerBlockReference());
        booking.cancel(CancellationReason.PAYMENT_FAILED, command.occurredAt());
        bookingRepository.save(booking);
        eventPublisher.publishBookingCancelled(booking, command.occurredAt());
    }
}
