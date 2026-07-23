package com.roadscanner.bookingservice.application.usecase.scheduled;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.model.SeatHold;
import com.roadscanner.bookingservice.domain.port.in.SweepStaleHolds;
import com.roadscanner.bookingservice.domain.port.out.BookingEventPublisher;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.bookingservice.domain.port.out.SeatHoldRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Implements {@link SweepStaleHolds} — the defensive, secondary sweep that reaches the same
 * outcome {@code Handle Seat Released} would, without waiting on an event
 * {@code provider-integration-service} doesn't yet publish
 * (docs/services/booking-service/events-consumed.md).
 */
public class SweepStaleHoldsService implements SweepStaleHolds {

    private final SeatHoldRepository seatHoldRepository;
    private final BookingRepository bookingRepository;
    private final ProviderIntegrationClient providerIntegrationClient;
    private final BookingEventPublisher eventPublisher;
    private final Clock clock;

    public SweepStaleHoldsService(SeatHoldRepository seatHoldRepository, BookingRepository bookingRepository,
                                   ProviderIntegrationClient providerIntegrationClient,
                                   BookingEventPublisher eventPublisher, Clock clock) {
        this.seatHoldRepository = seatHoldRepository;
        this.bookingRepository = bookingRepository;
        this.providerIntegrationClient = providerIntegrationClient;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public Result sweep() {
        Instant now = clock.instant();

        List<SeatHold> expiredHolds = seatHoldRepository.findAllExpiredBefore(now);
        for (SeatHold hold : expiredHolds) {
            seatHoldRepository.deleteById(hold.id());
        }

        List<Booking> staleBookings = bookingRepository.findPendingPaymentWithHoldExpiredBefore(now);
        for (Booking booking : staleBookings) {
            providerIntegrationClient.releaseSeat(booking.providerType(), booking.providerBlockReference());
            booking.cancel(CancellationReason.HOLD_EXPIRED, now);
            bookingRepository.save(booking);
            eventPublisher.publishBookingCancelled(booking, now);
        }

        return new Result(expiredHolds.size(), staleBookings.size());
    }
}
