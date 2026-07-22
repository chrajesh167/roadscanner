package com.roadscanner.inventoryservice.application.usecase.ingestion;

import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.port.in.IngestCancelledTrip;
import com.roadscanner.inventoryservice.domain.port.out.CatalogEventPublisher;
import com.roadscanner.inventoryservice.domain.port.out.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/** Implements {@link IngestCancelledTrip}. */
public class IngestCancelledTripService implements IngestCancelledTrip {

    private static final Logger log = LoggerFactory.getLogger(IngestCancelledTripService.class);

    private final TripRepository tripRepository;
    private final CatalogEventPublisher catalogEventPublisher;

    public IngestCancelledTripService(TripRepository tripRepository, CatalogEventPublisher catalogEventPublisher) {
        this.tripRepository = tripRepository;
        this.catalogEventPublisher = catalogEventPublisher;
    }

    @Override
    public void ingest(Command command) {
        TripId tripId = new TripId(command.tripId());
        Optional<Trip> existing = tripRepository.findById(tripId);
        if (existing.isEmpty()) {
            log.warn("Received TripCancelled for unknown trip {} — discarding", tripId);
            return;
        }
        Trip trip = existing.get();
        boolean applied = trip.cancel(command.occurredAt());
        tripRepository.save(trip);
        if (applied) {
            catalogEventPublisher.publishTripCancelled(trip, command.occurredAt());
        }
    }
}
