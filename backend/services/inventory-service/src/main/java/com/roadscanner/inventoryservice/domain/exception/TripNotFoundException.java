package com.roadscanner.inventoryservice.domain.exception;

import com.roadscanner.inventoryservice.domain.model.TripId;

public class TripNotFoundException extends InventoryServiceException {

    private final TripId tripId;

    public TripNotFoundException(TripId tripId) {
        super("No such trip: " + tripId);
        this.tripId = tripId;
    }

    public TripId tripId() {
        return tripId;
    }
}
