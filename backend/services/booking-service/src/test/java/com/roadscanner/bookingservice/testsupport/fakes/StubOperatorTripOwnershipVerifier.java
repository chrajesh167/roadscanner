package com.roadscanner.bookingservice.testsupport.fakes;

import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.out.OperatorTripOwnershipVerifier;

import java.util.UUID;

public final class StubOperatorTripOwnershipVerifier implements OperatorTripOwnershipVerifier {

    public boolean owns = false;

    @Override
    public boolean ownsTrip(UUID operatorId, TripId tripId) {
        return owns;
    }
}
