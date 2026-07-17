package com.roadscanner.searchservice.application.usecase.indexing;

import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.port.in.UpdateRatingSnapshot;
import com.roadscanner.searchservice.domain.port.out.SearchableTripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implements {@link UpdateRatingSnapshot}. See that port's Javadoc for why a missing projection
 * is a silent no-op — reviews are not on any latency- or consistency-critical path
 * (docs/architecture/event-catalog.md), so a rating update for a not-yet-indexed trip is simply
 * discarded rather than reconciled.
 */
@Transactional
public class RatingSnapshotUpdater implements UpdateRatingSnapshot {

    private static final Logger log = LoggerFactory.getLogger(RatingSnapshotUpdater.class);

    private final SearchableTripRepository repository;

    public RatingSnapshotUpdater(SearchableTripRepository repository) {
        this.repository = repository;
    }

    @Override
    public void update(UpdateRatingSnapshotCommand command) {
        Optional<SearchableTrip> existing = repository.findByTripId(command.tripId());
        if (existing.isEmpty()) {
            log.debug("Received ReviewSubmitted for trip {} with no existing projection — discarding",
                    command.tripId());
            return;
        }

        SearchableTrip trip = existing.get();
        boolean applied = trip.applyRatingUpdate(command.rating(), command.occurredAt());
        if (applied) {
            repository.save(trip);
            log.info("Updated rating snapshot for trip {} to {}", command.tripId(), command.rating());
        } else {
            log.debug("Ignored stale rating update for trip {}", command.tripId());
        }
    }
}
