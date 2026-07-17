package com.roadscanner.searchservice.domain.port.in;

import com.roadscanner.searchservice.domain.model.RatingSnapshot;
import com.roadscanner.searchservice.domain.model.TripId;

import java.time.Instant;
import java.util.Objects;

/**
 * Indexes a {@code ReviewSubmitted} event (docs/services/search-service/events-consumed.md) —
 * copies {@code review-service}'s already-computed aggregate onto the projection via
 * {@code SearchableTrip.applyRatingUpdate}. If no projection exists for this {@link TripId} yet
 * (search-service behind on the corresponding {@code TripPublished}), the implementation is a
 * no-op: per docs/architecture/event-catalog.md, "reviews are not on any latency- or
 * consistency-critical path," so simply discarding a rating update for a not-yet-indexed trip
 * is acceptable — the next {@code ReviewSubmitted} for that trip (or a manual reconciliation)
 * will catch it up.
 */
public interface UpdateRatingSnapshot {

    void update(UpdateRatingSnapshotCommand command);

    record UpdateRatingSnapshotCommand(TripId tripId, RatingSnapshot rating, Instant occurredAt) {
        public UpdateRatingSnapshotCommand {
            Objects.requireNonNull(tripId, "tripId must not be null");
            Objects.requireNonNull(rating, "rating must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
