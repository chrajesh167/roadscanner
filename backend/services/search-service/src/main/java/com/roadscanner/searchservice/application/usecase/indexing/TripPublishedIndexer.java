package com.roadscanner.searchservice.application.usecase.indexing;

import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.port.in.IndexTripPublished;
import com.roadscanner.searchservice.domain.port.out.SearchableTripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implements {@link IndexTripPublished}. A redelivery of an already-indexed trip (at-least-once
 * delivery per docs/architecture/event-catalog.md) is applied as an update via
 * {@code SearchableTrip.applyUpdate}, reusing that method's staleness/terminal-state
 * invariants rather than duplicating them for a "re-publish" case — see this port's Javadoc.
 */
@Transactional
public class TripPublishedIndexer implements IndexTripPublished {

    private static final Logger log = LoggerFactory.getLogger(TripPublishedIndexer.class);

    private final SearchableTripRepository repository;

    public TripPublishedIndexer(SearchableTripRepository repository) {
        this.repository = repository;
    }

    @Override
    public void index(IndexTripPublishedCommand command) {
        Optional<SearchableTrip> existing = repository.findByTripId(command.tripId());
        if (existing.isEmpty()) {
            SearchableTrip trip = SearchableTrip.publish(command.tripId(), command.operatorId(), command.operatorName(),
                    command.route(), command.schedule(), command.busType(), command.fare(), command.occurredAt());
            repository.save(trip);
            log.info("Indexed newly published trip {}", command.tripId());
            return;
        }

        SearchableTrip trip = existing.get();
        boolean applied = trip.applyUpdate(command.operatorName(), command.route(), command.schedule(),
                command.busType(), command.fare(), command.occurredAt());
        if (applied) {
            repository.save(trip);
            log.info("Applied redelivered TripPublished as an update for trip {}", command.tripId());
        } else {
            log.debug("Ignored stale or terminal redelivered TripPublished for trip {}", command.tripId());
        }
    }
}
