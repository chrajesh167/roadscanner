package com.roadscanner.bookingservice.application.usecase.scheduled;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.port.in.CompleteBooking;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Implements {@link CompleteBooking} — docs/requirements/actors.md's Scheduler/System Jobs
 * actor. No event is published for {@code COMPLETED} — not part of
 * docs/services/booking-service/events-published.md's contract. */
public class CompleteBookingService implements CompleteBooking {

    private final BookingRepository bookingRepository;
    private final Clock clock;

    public CompleteBookingService(BookingRepository bookingRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    @Override
    public Result completeDepartedTrips() {
        Instant now = clock.instant();
        List<Booking> departed = bookingRepository.findConfirmedWithDepartureBefore(now);
        int completedCount = 0;
        for (Booking booking : departed) {
            if (booking.complete(now)) {
                bookingRepository.save(booking);
                completedCount++;
            }
        }
        return new Result(completedCount);
    }
}
