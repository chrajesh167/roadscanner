package com.roadscanner.inventoryservice.domain.port.out;

import com.roadscanner.inventoryservice.domain.model.SeatLayout;
import com.roadscanner.inventoryservice.domain.model.TripId;

import java.util.Optional;

public interface SeatLayoutRepository {

    Optional<SeatLayout> findByTripId(TripId tripId);

    SeatLayout save(SeatLayout seatLayout);
}
