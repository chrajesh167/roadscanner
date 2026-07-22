package com.roadscanner.inventoryservice.testsupport.fakes;

import com.roadscanner.inventoryservice.domain.model.SeatLayout;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.out.SeatLayoutRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemorySeatLayoutRepository implements SeatLayoutRepository {

    private final Map<TripId, SeatLayout> layouts = new LinkedHashMap<>();

    @Override
    public Optional<SeatLayout> findByTripId(TripId tripId) {
        return Optional.ofNullable(layouts.get(tripId));
    }

    @Override
    public SeatLayout save(SeatLayout seatLayout) {
        layouts.put(seatLayout.tripId(), seatLayout);
        return seatLayout;
    }
}
