package com.roadscanner.bookingservice.testsupport.fakes;

import com.roadscanner.bookingservice.domain.model.TripId;
import com.roadscanner.bookingservice.domain.port.out.InventoryClient;

import java.util.Optional;
import java.util.function.Function;

/** A fully-configurable {@link InventoryClient} test double. */
public final class StubInventoryClient implements InventoryClient {

    public Function<TripId, Optional<TripSnapshot>> tripResult = tripId -> Optional.empty();
    public Function<TripId, Optional<SeatLayoutView>> seatLayoutResult = tripId -> Optional.empty();
    public Function<TripId, Optional<ProviderMappingView>> providerMappingResult = tripId -> Optional.empty();

    @Override
    public Optional<TripSnapshot> getTrip(TripId tripId) {
        return tripResult.apply(tripId);
    }

    @Override
    public Optional<SeatLayoutView> getSeatLayout(TripId tripId) {
        return seatLayoutResult.apply(tripId);
    }

    @Override
    public Optional<ProviderMappingView> getProviderMapping(TripId tripId) {
        return providerMappingResult.apply(tripId);
    }
}
