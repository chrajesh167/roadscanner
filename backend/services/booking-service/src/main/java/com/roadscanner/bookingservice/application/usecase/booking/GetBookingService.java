package com.roadscanner.bookingservice.application.usecase.booking;

import com.roadscanner.bookingservice.domain.exception.BookingNotFoundException;
import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.port.in.GetBooking;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;
import com.roadscanner.bookingservice.domain.port.out.OperatorTripOwnershipVerifier;

/** Implements {@link GetBooking} — ownership-checked, per
 * docs/services/booking-service/boundaries.md's "Booking ↔ Auth". A denied request surfaces as
 * {@code BookingNotFoundException} (404), never a 403 — enumeration protection. */
public class GetBookingService implements GetBooking {

    private final BookingRepository bookingRepository;
    private final OperatorTripOwnershipVerifier ownershipVerifier;

    public GetBookingService(BookingRepository bookingRepository, OperatorTripOwnershipVerifier ownershipVerifier) {
        this.bookingRepository = bookingRepository;
        this.ownershipVerifier = ownershipVerifier;
    }

    @Override
    public Result get(Command command) {
        Booking booking = bookingRepository.findById(command.bookingId())
                .filter(b -> canView(b, command.requester()))
                .orElseThrow(() -> new BookingNotFoundException(command.bookingId()));
        return new Result(booking);
    }

    private boolean canView(Booking booking, RequesterContext requester) {
        return switch (requester.role()) {
            case ADMIN, SUPPORT -> true;
            case TRAVELER -> booking.isOwnedBy(requester.requesterId());
            case OPERATOR -> ownershipVerifier.ownsTrip(requester.requesterId(), booking.tripId());
        };
    }
}
