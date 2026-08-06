package com.roadscanner.bookingservice.application.usecase.booking;

import com.roadscanner.bookingservice.domain.exception.SeatHoldExpiredException;
import com.roadscanner.bookingservice.domain.exception.SeatHoldNotFoundException;
import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.Passenger;
import com.roadscanner.bookingservice.domain.model.SeatHold;
import com.roadscanner.bookingservice.domain.port.in.CreateBooking;
import com.roadscanner.bookingservice.domain.port.out.BookingEventPublisher;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;
import com.roadscanner.bookingservice.domain.port.out.SeatHoldRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Implements {@link CreateBooking}. Validates the hold's {@code expiresAt} locally — the
 * resolution to docs/services/booking-service/boundaries.md's "Known Gap: No Read-Only
 * Reservation-Status Check" — then creates the {@code Booking} and consumes the hold, so a hold
 * token becomes at most one booking (docs/architecture/booking-flow.md's idempotency
 * requirement).
 */
public class CreateBookingService implements CreateBooking {

    private final SeatHoldRepository seatHoldRepository;
    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;
    private final Clock clock;

    public CreateBookingService(SeatHoldRepository seatHoldRepository, BookingRepository bookingRepository,
                                 BookingEventPublisher eventPublisher, Clock clock) {
        this.seatHoldRepository = seatHoldRepository;
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public Result create(Command command) {
        SeatHold hold = seatHoldRepository.findById(command.seatHoldId())
                .filter(h -> h.isOwnedBy(command.travelerId()))
                .orElseThrow(() -> new SeatHoldNotFoundException(command.seatHoldId()));

        Instant now = clock.instant();
        if (hold.isExpired(now)) {
            throw new SeatHoldExpiredException(hold.id());
        }

        // Taken from the hold, not from the caller: these travellers are the ones the provider
        // already bound to these seats, and accepting a second list here would let a booking
        // disagree with the reservation it is built on.
        List<Passenger> passengers = hold.passengers();

        Booking booking = Booking.create(BookingId.generate(), command.travelerId(), hold.tripId(),
                hold.tripDepartureTime(), hold.providerType(), hold.providerTripId(), hold.providerBlockReference(),
                hold.expiresAt(), passengers, command.contact(), hold.fare(), now);
        bookingRepository.save(booking);
        seatHoldRepository.deleteById(hold.id());
        eventPublisher.publishBookingCreated(booking, now);

        return new Result(booking.id(), booking.status());
    }

}
