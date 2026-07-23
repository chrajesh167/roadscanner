package com.roadscanner.bookingservice.adapter.out.client;

import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.out.OperatorTripOwnershipVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The shipped {@link OperatorTripOwnershipVerifier} adapter — {@code operator-service} does not
 * exist yet, so there is no real trip-ownership registry to check against.
 * <strong>Fails closed: always returns {@code false}</strong> — NFR-7's "refuse the request
 * rather than risk an inconsistent state," applied to authorization. Operators cannot use
 * {@code List Trip Bookings} or view a single booking against their trip until
 * {@code operator-service} exists and this adapter is replaced with a real check.
 */
@Component
class DefaultOperatorTripOwnershipVerifier implements OperatorTripOwnershipVerifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultOperatorTripOwnershipVerifier.class);

    @Override
    public boolean ownsTrip(UUID operatorId, TripId tripId) {
        log.info("No operator-service to verify operator {} owns trip {} — denying (fail-closed interim behavior)",
                operatorId, tripId);
        return false;
    }
}
