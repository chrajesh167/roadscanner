package com.roadscanner.bookingservice.application.usecase.verification;

import com.roadscanner.bookingservice.domain.port.in.VerifyBooking;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;

/** Implements {@link VerifyBooking} — backs FR-7.2, the only inbound service-to-service call any
 * other service makes against this service (docs/services/booking-service/boundaries.md's
 * "Relationship to `review-service`"). */
public class VerifyBookingService implements VerifyBooking {

    private final BookingRepository bookingRepository;

    public VerifyBookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public Result verify(Command command) {
        boolean verified = bookingRepository.existsCompletedByTravelerIdAndTripId(command.travelerId(),
                command.tripId());
        return new Result(verified);
    }
}
