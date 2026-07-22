package com.roadscanner.inventoryservice.testsupport.fakes;

import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.out.TripRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryTripRepository implements TripRepository {

    private final Map<TripId, Trip> trips = new LinkedHashMap<>();

    @Override
    public Optional<Trip> findById(TripId id) {
        return Optional.ofNullable(trips.get(id));
    }

    @Override
    public Trip save(Trip trip) {
        trips.put(trip.id(), trip);
        return trip;
    }

    @Override
    public List<Trip> findByOperatorId(UUID operatorId) {
        return trips.values().stream().filter(t -> t.operatorId().map(operatorId::equals).orElse(false)).toList();
    }
}
