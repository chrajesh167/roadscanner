package com.roadscanner.bookingservice.domain.port.out;

import com.roadscanner.bookingservice.domain.model.TripId;

import java.util.UUID;

/**
 * Verifies an {@code OPERATOR} requester owns a given trip, backing {@code List Trip Bookings}
 * (docs/services/booking-service/domain-port-in.ListTripBookings). {@code operator-service} does
 * not exist yet, so there is no real ownership registry to check against.
 *
 * <p>The shipped adapter ({@code adapter.out.client.DefaultOperatorTripOwnershipVerifier})
 * <strong>fails closed — always returns {@code false}</strong> — NFR-7's "refuse the request
 * rather than risk an inconsistent state," applied to authorization instead of booking
 * correctness. Operators cannot use {@code List Trip Bookings} until {@code operator-service}
 * exists and this adapter is replaced with a real check.
 */
public interface OperatorTripOwnershipVerifier {

    boolean ownsTrip(UUID operatorId, TripId tripId);
}
