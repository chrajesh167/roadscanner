package com.roadscanner.searchservice.domain.port.in;

import com.roadscanner.searchservice.domain.model.TripId;

import java.time.Instant;
import java.util.Objects;

/**
 * Indexes a {@code TripCancelled} event (docs/services/search-service/events-consumed.md) —
 * marks the projection's bookability flag false via {@code SearchableTrip.cancel}, which is
 * unconditionally applied once and idempotent thereafter (that method's Javadoc). If no
 * projection exists for this {@link TripId} yet, the implementation is a no-op: there is
 * nothing to cancel, and nothing will ever try to index it as bookable again once the
 * corresponding (also-delivered) {@code TripPublished} arrives — see
 * docs/services/search-service/events-consumed.md's "Ordering Edge Case" for why that sequencing
 * is guaranteed within a partition.
 */
public interface IndexTripCancelled {

    void index(IndexTripCancelledCommand command);

    record IndexTripCancelledCommand(TripId tripId, Instant occurredAt) {
        public IndexTripCancelledCommand {
            Objects.requireNonNull(tripId, "tripId must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }
    }
}
