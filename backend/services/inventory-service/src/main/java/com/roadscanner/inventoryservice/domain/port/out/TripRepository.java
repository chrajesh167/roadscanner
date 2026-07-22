package com.roadscanner.inventoryservice.domain.port.out;

import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.model.TripId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository {

    Optional<Trip> findById(TripId id);

    Trip save(Trip trip);

    /** Backs {@code OperatorUpdated} ingestion — every trip whose denormalized operator name
     * needs refreshing. */
    List<Trip> findByOperatorId(UUID operatorId);
}
