package com.roadscanner.inventoryservice.domain.port.in;

import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.model.TripId;

import java.util.Objects;

/** Provider type + native trip id for a catalog trip — consumed by {@code booking-service}
 * (docs/services/inventory-service/use-cases.md). Raises
 * {@link com.roadscanner.inventoryservice.domain.exception.ProviderMappingNotFoundException}. */
public interface GetProviderMapping {

    Result get(Command command);

    record Command(TripId tripId) {
        public Command {
            Objects.requireNonNull(tripId, "tripId must not be null");
        }
    }

    record Result(ProviderMapping mapping) {
        public Result {
            Objects.requireNonNull(mapping, "mapping must not be null");
        }
    }
}
