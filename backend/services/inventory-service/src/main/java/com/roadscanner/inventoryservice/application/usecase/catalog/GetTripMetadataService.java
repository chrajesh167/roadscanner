package com.roadscanner.inventoryservice.application.usecase.catalog;

import com.roadscanner.inventoryservice.domain.exception.TripNotFoundException;
import com.roadscanner.inventoryservice.domain.port.in.GetTripMetadata;
import com.roadscanner.inventoryservice.domain.port.out.TripRepository;

/** Implements {@link GetTripMetadata}. */
public class GetTripMetadataService implements GetTripMetadata {

    private final TripRepository tripRepository;

    public GetTripMetadataService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    @Override
    public Result get(Command command) {
        return new Result(tripRepository.findById(command.tripId())
                .orElseThrow(() -> new TripNotFoundException(command.tripId())));
    }
}
