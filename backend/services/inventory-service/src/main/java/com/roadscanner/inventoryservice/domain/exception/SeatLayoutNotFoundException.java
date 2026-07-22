package com.roadscanner.inventoryservice.domain.exception;

import com.roadscanner.inventoryservice.domain.model.TripId;

public class SeatLayoutNotFoundException extends InventoryServiceException {

    private final TripId tripId;

    public SeatLayoutNotFoundException(TripId tripId) {
        super("No seat layout for trip: " + tripId);
        this.tripId = tripId;
    }

    public TripId tripId() {
        return tripId;
    }
}
