package com.roadscanner.inventoryservice.domain.port.in;

import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.model.TripId;

import java.util.Objects;

/** Catalog shape for a trip — route, schedule, operator, fare, bookable flag. Never live seat
 * data of any kind (docs/services/inventory-service/use-cases.md). Raises
 * {@link com.roadscanner.inventoryservice.domain.exception.TripNotFoundException}. */
public interface GetTripMetadata {

    Result get(Command command);

    record Command(TripId tripId) {
        public Command {
            Objects.requireNonNull(tripId, "tripId must not be null");
        }
    }

    record Result(Trip trip) {
        public Result {
            Objects.requireNonNull(trip, "trip must not be null");
        }
    }
}
