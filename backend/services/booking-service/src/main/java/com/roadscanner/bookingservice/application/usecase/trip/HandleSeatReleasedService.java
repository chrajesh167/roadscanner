package com.roadscanner.bookingservice.application.usecase.trip;

import com.roadscanner.bookingservice.domain.model.BookingStatus;
import com.roadscanner.bookingservice.domain.model.CancellationReason;
import com.roadscanner.bookingservice.domain.port.in.HandleSeatReleased;
import com.roadscanner.bookingservice.domain.port.out.BookingEventPublisher;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;
import com.roadscanner.bookingservice.domain.port.out.SeatHoldRepository;

/**
 * Implements {@link HandleSeatReleased} — covers a hold that expires before the traveler ever
 * reaches {@code payment-service}, where no {@code PaymentFailed}/{@code PaymentTimedOut} will
 * ever arrive (docs/services/booking-service/use-cases.md). Handles both cases: an outstanding
 * {@code SeatHold} with no {@code Booking} yet (simply discarded), and a {@code PENDING_PAYMENT}
 * booking whose reservation this event refers to (cancelled, reason {@code HOLD_EXPIRED}).
 */
public class HandleSeatReleasedService implements HandleSeatReleased {

    private final SeatHoldRepository seatHoldRepository;
    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;

    public HandleSeatReleasedService(SeatHoldRepository seatHoldRepository, BookingRepository bookingRepository,
                                      BookingEventPublisher eventPublisher) {
        this.seatHoldRepository = seatHoldRepository;
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void handle(Command command) {
        seatHoldRepository.findByProviderBlockReference(command.providerBlockReference())
                .ifPresent(hold -> seatHoldRepository.deleteById(hold.id()));

        bookingRepository.findByProviderBlockReference(command.providerBlockReference())
                .filter(b -> b.status() == BookingStatus.PENDING_PAYMENT)
                .ifPresent(booking -> {
                    booking.cancel(CancellationReason.HOLD_EXPIRED, command.occurredAt());
                    bookingRepository.save(booking);
                    eventPublisher.publishBookingCancelled(booking, command.occurredAt());
                });
    }
}
