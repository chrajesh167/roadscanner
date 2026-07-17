package com.roadscanner.searchservice.domain.port.in;

import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.TripId;

import java.time.Instant;
import java.util.Objects;

/**
 * Indexes a {@code TripUpdated} event (docs/services/search-service/events-consumed.md).
 * Carries a full snapshot of the trip's shape, not a partial patch — see
 * {@code SearchableTrip.applyUpdate}'s Javadoc for why full-replace is what keeps this
 * idempotent under at-least-once redelivery.
 *
 * <p>If no projection exists yet for this {@link TripId}, the implementation logs a warning
 * and discards the event rather than attempting a partial create — this command deliberately
 * does not carry {@code operatorId} (an update never changes it), so there is not enough
 * information here to construct a new {@code SearchableTrip} via {@code publish}. This case is
 * expected to never occur in practice: docs/services/search-service/events-consumed.md's
 * "Ordering Edge Case" guarantees {@code TripPublished} for a given trip is always delivered
 * first, since it shares that trip's partition key. If it does occur, it signals a genuine
 * platform-level ordering violation worth investigating, not a condition this port papers over.
 */
public interface IndexTripUpdated {

    void index(IndexTripUpdatedCommand command);

    record IndexTripUpdatedCommand(TripId tripId, String operatorName, Route route, Schedule schedule,
                                    BusType busType, FareSnapshot fare, Instant occurredAt) {
        public IndexTripUpdatedCommand {
            Objects.requireNonNull(tripId, "tripId must not be null");
            Objects.requireNonNull(operatorName, "operatorName must not be null");
            Objects.requireNonNull(route, "route must not be null");
            Objects.requireNonNull(schedule, "schedule must not be null");
            Objects.requireNonNull(busType, "busType must not be null");
            Objects.requireNonNull(fare, "fare must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
