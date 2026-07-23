package com.roadscanner.bookingservice.application.usecase.booking;

import com.roadscanner.bookingservice.domain.model.Role;
import com.roadscanner.bookingservice.domain.port.in.ListTripBookings;
import com.roadscanner.bookingservice.domain.port.out.BookingRepository;
import com.roadscanner.bookingservice.domain.port.out.OperatorTripOwnershipVerifier;
import org.springframework.security.access.AccessDeniedException;

/**
 * Implements {@link ListTripBookings}. Trip existence is not sensitive the way a specific
 * booking is, so a denied request here surfaces as a genuine {@code 403} (via Spring Security's
 * {@link AccessDeniedException}), not the enumeration-protecting {@code 404} pattern used
 * elsewhere in this service. See the port's own Javadoc for the fail-closed
 * {@code OperatorTripOwnershipVerifier} interim behavior.
 */
public class ListTripBookingsService implements ListTripBookings {

    private final BookingRepository bookingRepository;
    private final OperatorTripOwnershipVerifier ownershipVerifier;

    public ListTripBookingsService(BookingRepository bookingRepository,
                                    OperatorTripOwnershipVerifier ownershipVerifier) {
        this.bookingRepository = bookingRepository;
        this.ownershipVerifier = ownershipVerifier;
    }

    @Override
    public Result list(Command command) {
        boolean allowed = command.requester().isPrivileged()
                || (command.requester().role() == Role.OPERATOR
                        && ownershipVerifier.ownsTrip(command.requester().requesterId(), command.tripId()));
        if (!allowed) {
            throw new AccessDeniedException("Not authorized to view bookings for trip " + command.tripId());
        }
        return new Result(bookingRepository.findByTripId(command.tripId()));
    }
}
