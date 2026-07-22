package com.roadscanner.inventoryservice.application.usecase.ingestion;

import com.roadscanner.inventoryservice.domain.model.OperatorRef;
import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.port.in.IngestOperatorUpdate;
import com.roadscanner.inventoryservice.domain.port.out.CatalogEventPublisher;
import com.roadscanner.inventoryservice.domain.port.out.OperatorRefRepository;
import com.roadscanner.inventoryservice.domain.port.out.TripRepository;

import java.time.Clock;

/** Implements {@link IngestOperatorUpdate} — refreshes the denormalized name on the
 * {@code OperatorRef} and every affected {@code Trip}, then re-publishes. */
public class IngestOperatorUpdateService implements IngestOperatorUpdate {

    private final OperatorRefRepository operatorRefRepository;
    private final TripRepository tripRepository;
    private final CatalogEventPublisher catalogEventPublisher;
    private final Clock clock;

    public IngestOperatorUpdateService(OperatorRefRepository operatorRefRepository, TripRepository tripRepository,
                                        CatalogEventPublisher catalogEventPublisher, Clock clock) {
        this.operatorRefRepository = operatorRefRepository;
        this.tripRepository = tripRepository;
        this.catalogEventPublisher = catalogEventPublisher;
        this.clock = clock;
    }

    @Override
    public void ingest(Command command) {
        operatorRefRepository.save(OperatorRef.of(command.operatorId(), command.displayName()));

        for (Trip trip : tripRepository.findByOperatorId(command.operatorId())) {
            trip.refreshOperatorDisplayName(command.displayName());
            tripRepository.save(trip);
        }

        catalogEventPublisher.publishOperatorUpdated(command.operatorId(), command.displayName(), clock.instant());
    }
}
