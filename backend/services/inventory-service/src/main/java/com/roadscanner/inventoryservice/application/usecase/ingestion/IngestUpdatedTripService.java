package com.roadscanner.inventoryservice.application.usecase.ingestion;

import com.roadscanner.inventoryservice.domain.model.FareAmount;
import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.model.TripSchedule;
import com.roadscanner.inventoryservice.domain.port.in.IngestUpdatedTrip;
import com.roadscanner.inventoryservice.domain.port.out.CatalogEventPublisher;
import com.roadscanner.inventoryservice.domain.port.out.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Currency;
import java.util.Optional;

/** Implements {@link IngestUpdatedTrip}. */
public class IngestUpdatedTripService implements IngestUpdatedTrip {

    private static final Logger log = LoggerFactory.getLogger(IngestUpdatedTripService.class);

    private final TripRepository tripRepository;
    private final CatalogEventPublisher catalogEventPublisher;

    public IngestUpdatedTripService(TripRepository tripRepository, CatalogEventPublisher catalogEventPublisher) {
        this.tripRepository = tripRepository;
        this.catalogEventPublisher = catalogEventPublisher;
    }

    @Override
    public void ingest(Command command) {
        TripId tripId = new TripId(command.tripId());
        Optional<Trip> existing = tripRepository.findById(tripId);
        if (existing.isEmpty()) {
            // This event doesn't carry enough data to construct a new projection — per
            // docs/services/inventory-service/events-consumed.md, log and discard rather than
            // attempt a partial create. Ordering (partitioned by trip id) means TripPublished
            // always precedes this for the same trip, so this is not expected in practice.
            log.warn("Received TripUpdated for unknown trip {} — discarding", tripId);
            return;
        }
        Trip trip = existing.get();
        boolean applied = trip.applyUpdate(null, command.origin(), command.destination(),
                new TripSchedule(command.departureTime(), command.arrivalTime()), command.operatorName(),
                command.busTypeCategory(), command.amenities(),
                new FareAmount(command.fareAmount(), Currency.getInstance(command.fareCurrency()), command.occurredAt()),
                command.occurredAt());
        if (!applied) {
            return;
        }
        tripRepository.save(trip);
        catalogEventPublisher.publishTripUpdated(trip, command.occurredAt());
    }
}
