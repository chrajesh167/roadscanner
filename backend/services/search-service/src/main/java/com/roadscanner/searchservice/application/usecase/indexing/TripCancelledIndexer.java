package com.roadscanner.searchservice.application.usecase.indexing;

import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.port.in.IndexTripCancelled;
import com.roadscanner.searchservice.domain.port.out.SearchableTripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implements {@link IndexTripCancelled}. A missing projection is a silent no-op — there is
 * nothing to cancel, and per this port's Javadoc, the corresponding {@code TripPublished} (also
 * guaranteed same-partition) will never resurrect it as bookable once this event has been
 * consumed... except it hasn't been consumed yet in this scenario, which is exactly why this is
 * a no-op rather than an error: if {@code TripCancelled} somehow arrives first, the situation
 * self-corrects the moment {@code TripPublished} is processed, since {@code SearchableTrip.publish}
 * always starts a fresh projection bookable — this indexer does not need to remember "this trip
 * was pre-emptively cancelled."
 */
@Transactional
public class TripCancelledIndexer implements IndexTripCancelled {

    private static final Logger log = LoggerFactory.getLogger(TripCancelledIndexer.class);

    private final SearchableTripRepository repository;

    public TripCancelledIndexer(SearchableTripRepository repository) {
        this.repository = repository;
    }

    @Override
    public void index(IndexTripCancelledCommand command) {
        Optional<SearchableTrip> existing = repository.findByTripId(command.tripId());
        if (existing.isEmpty()) {
            log.debug("Received TripCancelled for trip {} with no existing projection — nothing to cancel",
                    command.tripId());
            return;
        }

        SearchableTrip trip = existing.get();
        boolean applied = trip.cancel(command.occurredAt());
        if (applied) {
            repository.save(trip);
            log.info("Cancelled indexed trip {}", command.tripId());
        } else {
            log.debug("Trip {} was already cancelled — idempotent no-op", command.tripId());
        }
    }
}
