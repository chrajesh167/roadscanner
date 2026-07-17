package com.roadscanner.searchservice.application.usecase.indexing;

import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.port.in.IndexTripUpdated;
import com.roadscanner.searchservice.domain.port.out.SearchableTripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implements {@link IndexTripUpdated}. See that port's Javadoc for why a missing projection is
 * logged and discarded rather than defensively created — this event does not carry enough data
 * to construct one, and the case is not expected to occur given the platform's partition-key
 * ordering guarantee.
 */
@Transactional
public class TripUpdatedIndexer implements IndexTripUpdated {

    private static final Logger log = LoggerFactory.getLogger(TripUpdatedIndexer.class);

    private final SearchableTripRepository repository;

    public TripUpdatedIndexer(SearchableTripRepository repository) {
        this.repository = repository;
    }

    @Override
    public void index(IndexTripUpdatedCommand command) {
        Optional<SearchableTrip> existing = repository.findByTripId(command.tripId());
        if (existing.isEmpty()) {
            log.warn("Received TripUpdated for trip {} with no existing projection — discarding; "
                    + "this indicates an ordering violation, since TripPublished should always precede it "
                    + "within the same partition", command.tripId());
            return;
        }

        SearchableTrip trip = existing.get();
        boolean applied = trip.applyUpdate(command.operatorName(), command.route(), command.schedule(),
                command.busType(), command.fare(), command.occurredAt());
        if (applied) {
            repository.save(trip);
            log.info("Applied TripUpdated for trip {}", command.tripId());
        } else {
            log.debug("Ignored stale or terminal TripUpdated for trip {}", command.tripId());
        }
    }
}
