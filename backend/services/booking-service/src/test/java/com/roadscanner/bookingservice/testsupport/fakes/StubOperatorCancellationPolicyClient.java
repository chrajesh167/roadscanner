package com.roadscanner.bookingservice.testsupport.fakes;

import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.out.OperatorCancellationPolicyClient;

import java.math.BigDecimal;

public final class StubOperatorCancellationPolicyClient implements OperatorCancellationPolicyClient {

    public CancellationPolicy policy = new CancellationPolicy(true, BigDecimal.ZERO, "INR");

    @Override
    public CancellationPolicy getCancellationPolicy(TripId tripId) {
        return policy;
    }
}
