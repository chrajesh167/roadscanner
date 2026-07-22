package com.roadscanner.inventoryservice.domain.port.in;

import com.roadscanner.inventoryservice.domain.model.TripId;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * The availability facade — backs {@code search-service}'s existing, frozen
 * {@code GET /api/v1/inventory/trips/{tripId}/availability} contract
 * (docs/services/inventory-service/api-summary.md). Resolves the trip's {@code ProviderMapping}
 * and asks {@code provider-integration-service} live, every time; never caches the answer
 * (docs/services/inventory-service/boundaries.md).
 *
 * Never throws for "provider unreachable" or "no mapping" — both degrade to
 * {@link Result#availableSeats()} being empty, matching {@code search-service}'s own
 * already-shipped "degrade, not fail" handling of a non-2xx/error response from this endpoint.
 */
public interface GetTripAvailability {

    Result get(Command command);

    record Command(TripId tripId) {
        public Command {
            Objects.requireNonNull(tripId, "tripId must not be null");
        }
    }

    record Result(OptionalInt availableSeats) {
        public Result {
            Objects.requireNonNull(availableSeats, "availableSeats must not be null");
        }

        public static Result known(int seats) {
            return new Result(OptionalInt.of(seats));
        }

        public static Result unknown() {
            return new Result(OptionalInt.empty());
        }
    }
}
