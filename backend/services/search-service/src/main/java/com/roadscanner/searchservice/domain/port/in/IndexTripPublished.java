package com.roadscanner.searchservice.domain.port.in;

import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.TripId;

import java.time.Instant;
import java.util.Objects;

/**
 * Indexes a {@code TripPublished} event (docs/services/search-service/events-consumed.md,
 * "Index a Newly Published Trip"). If a projection for this {@link TripId} already exists
 * (a redelivery — at-least-once delivery per docs/architecture/event-catalog.md), the
 * implementation applies this as an update via {@code SearchableTrip.applyUpdate}, reusing that
 * method's staleness/terminal-state invariants rather than duplicating them for a "re-publish"
 * case.
 */
public interface IndexTripPublished {

    void index(IndexTripPublishedCommand command);

    record IndexTripPublishedCommand(TripId tripId, OperatorId operatorId, String operatorName, Route route,
                                      Schedule schedule, BusType busType, FareSnapshot fare, Instant occurredAt) {
        public IndexTripPublishedCommand {
            Objects.requireNonNull(tripId, "tripId must not be null");
            Objects.requireNonNull(operatorId, "operatorId must not be null");
            Objects.requireNonNull(operatorName, "operatorName must not be null");
            Objects.requireNonNull(route, "route must not be null");
            Objects.requireNonNull(schedule, "schedule must not be null");
            Objects.requireNonNull(busType, "busType must not be null");
            Objects.requireNonNull(fare, "fare must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
