package com.roadscanner.inventoryservice.domain.port.in;

import com.roadscanner.inventoryservice.domain.model.SeatLayout;
import com.roadscanner.inventoryservice.domain.model.TripId;

import java.util.Objects;

/** The static seat layout for a trip — shape only, never status
 * (docs/services/inventory-service/use-cases.md). Raises
 * {@link com.roadscanner.inventoryservice.domain.exception.SeatLayoutNotFoundException}. */
public interface GetSeatLayout {

    Result get(Command command);

    record Command(TripId tripId) {
        public Command {
            Objects.requireNonNull(tripId, "tripId must not be null");
        }
    }

    record Result(SeatLayout seatLayout) {
        public Result {
            Objects.requireNonNull(seatLayout, "seatLayout must not be null");
        }
    }
}
