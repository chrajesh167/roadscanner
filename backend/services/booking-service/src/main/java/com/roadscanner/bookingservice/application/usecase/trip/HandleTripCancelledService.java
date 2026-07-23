package com.roadscanner.bookingservice.application.usecase.trip;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.port.in.HandleTripCancelled;
import com.roadscanner.bookingservice.domain.port.out.BookingEventPublisher;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.bookingservice.domain.port.out.RefundRequestPort;

import java.util.List;

/**
 * Implements {@link HandleTripCancelled} — docs/architecture/booking-flow.md step 7. Every
 * {@code CONFIRMED} booking gets a full refund regardless of the normal cancellation-fee policy
 * (the traveler didn't cause this, so no {@code operator-service} policy lookup is needed here,
 * unlike traveler-initiated cancellation) and, like every post-confirmation cancellation, no
 * provider-side reversal is attempted (docs/services/booking-service/boundaries.md's "Known
 * Gap").
 */
public class HandleTripCancelledService implements HandleTripCancelled {

    private final BookingRepository bookingRepository;
    private final ProviderIntegrationClient providerIntegrationClient;
    private final RefundRequestPort refundRequestPort;
    private final BookingEventPublisher eventPublisher;

    public HandleTripCancelledService(BookingRepository bookingRepository,
                                       ProviderIntegrationClient providerIntegrationClient,
                                       RefundRequestPort refundRequestPort, BookingEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.providerIntegrationClient = providerIntegrationClient;
        this.refundRequestPort = refundRequestPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void handle(Command command) {
        List<Booking> affected = bookingRepository.findByTripIdAndStatusIn(command.tripId(),
                List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED));

        for (Booking booking : affected) {
            if (booking.status() == BookingStatus.PENDING_PAYMENT) {
                providerIntegrationClient.releaseSeat(booking.providerType(), booking.providerBlockReference());
                booking.cancel(CancellationReason.TRIP_CANCELLED, command.occurredAt());
            } else {
                booking.cancel(CancellationReason.TRIP_CANCELLED, command.occurredAt());
                booking.paymentReference()
                        .ifPresent(ref -> refundRequestPort.requestRefund(booking.id(), ref, null));
            }
            bookingRepository.save(booking);
            eventPublisher.publishBookingCancelled(booking, command.occurredAt());
        }
    }
}
