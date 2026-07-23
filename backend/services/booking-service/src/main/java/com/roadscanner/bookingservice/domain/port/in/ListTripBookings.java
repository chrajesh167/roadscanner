package com.roadscanner.bookingservice.domain.port.in;

import com.roadscanner.bookingservice.domain.model.Booking;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.model.TripId;

import java.util.List;
import java.util.Objects;

/**
 * Every booking against a trip the requesting operator owns (FR-5.5, `api-inventory.md`'s
 * "operator-portal (view own trip's bookings)"). {@code ADMIN}/{@code SUPPORT} may always call
 * this; an {@code OPERATOR} requester's trip-ownership is verified via
 * {@code domain.port.out.OperatorTripOwnershipVerifier}.
 *
 * <p><strong>Documented interim behavior — not resolved further here</strong>
 * (docs/services/booking-service/boundaries.md's "Relationship to `operator-service`"):
 * {@code operator-service} does not exist yet, so there is no real trip-ownership registry to
 * check an {@code OPERATOR} requester against. The shipped adapter for
 * {@code OperatorTripOwnershipVerifier} fails closed (denies) for every {@code OPERATOR}
 * request rather than guessing or allowing broad access — NFR-7's "refuse the request rather
 * than risk an inconsistent state," applied to authorization. This is a real, flagged
 * limitation: operators cannot use this endpoint until {@code operator-service} exists.
 */
public interface ListTripBookings {

    Result list(Command command);

    record Command(TripId tripId, RequesterContext requester) {
        public Command {
            Objects.requireNonNull(tripId, "tripId must not be null");
            Objects.requireNonNull(requester, "requester must not be null");
        }
    }

    record Result(List<Booking> bookings) {
    }
}
