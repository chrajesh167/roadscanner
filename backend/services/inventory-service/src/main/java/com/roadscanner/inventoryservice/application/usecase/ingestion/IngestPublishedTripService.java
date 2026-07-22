package com.roadscanner.inventoryservice.application.usecase.ingestion;

import com.roadscanner.inventoryservice.domain.model.FareAmount;
import com.roadscanner.inventoryservice.domain.model.OperatorRef;
import com.roadscanner.inventoryservice.domain.model.Seat;
import com.roadscanner.inventoryservice.domain.model.SeatLayout;
import com.roadscanner.inventoryservice.domain.model.SeatNumber;
import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.model.TripSchedule;
import com.roadscanner.inventoryservice.domain.port.in.IngestPublishedTrip;
import com.roadscanner.inventoryservice.domain.port.out.CatalogEventPublisher;
import com.roadscanner.inventoryservice.domain.port.out.OperatorRefRepository;
import com.roadscanner.inventoryservice.domain.port.out.SeatLayoutRepository;
import com.roadscanner.inventoryservice.domain.port.out.TripRepository;

import java.util.Currency;
import java.util.List;

/** Implements {@link IngestPublishedTrip}. */
public class IngestPublishedTripService implements IngestPublishedTrip {

    private final TripRepository tripRepository;
    private final SeatLayoutRepository seatLayoutRepository;
    private final OperatorRefRepository operatorRefRepository;
    private final CatalogEventPublisher catalogEventPublisher;

    public IngestPublishedTripService(TripRepository tripRepository, SeatLayoutRepository seatLayoutRepository,
                                       OperatorRefRepository operatorRefRepository,
                                       CatalogEventPublisher catalogEventPublisher) {
        this.tripRepository = tripRepository;
        this.seatLayoutRepository = seatLayoutRepository;
        this.operatorRefRepository = operatorRefRepository;
        this.catalogEventPublisher = catalogEventPublisher;
    }

    @Override
    public void ingest(Command command) {
        // Seed the operator reference on first sighting; renames afterward flow only through
        // IngestOperatorUpdateService — this is not the source of truth for the name going forward.
        if (operatorRefRepository.findById(command.operatorId()).isEmpty()) {
            operatorRefRepository.save(OperatorRef.of(command.operatorId(), command.operatorName()));
        }

        TripId tripId = new TripId(command.tripId());
        Trip trip = Trip.ingestFirstParty(tripId, null, command.origin(), command.destination(),
                new TripSchedule(command.departureTime(), command.arrivalTime()), command.operatorId(),
                command.operatorName(), null, command.busTypeCategory(), command.amenities(),
                new FareAmount(command.fareAmount(), Currency.getInstance(command.fareCurrency()), command.occurredAt()),
                command.occurredAt());
        tripRepository.save(trip);

        List<Seat> seats = command.seatLayout().stream()
                .map(entry -> new Seat(new SeatNumber(entry.seatNumber()), entry.deck(), entry.seatType(),
                        entry.wheelchairAccessible(), entry.position()))
                .toList();
        seatLayoutRepository.save(new SeatLayout(tripId, seats));

        catalogEventPublisher.publishTripPublished(trip, command.occurredAt());
    }
}
