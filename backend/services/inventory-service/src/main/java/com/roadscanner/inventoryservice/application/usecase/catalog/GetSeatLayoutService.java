package com.roadscanner.inventoryservice.application.usecase.catalog;

import com.roadscanner.inventoryservice.domain.exception.SeatLayoutNotFoundException;
import com.roadscanner.inventoryservice.domain.port.in.GetSeatLayout;
import com.roadscanner.inventoryservice.domain.port.out.SeatLayoutRepository;

/** Implements {@link GetSeatLayout}. */
public class GetSeatLayoutService implements GetSeatLayout {

    private final SeatLayoutRepository seatLayoutRepository;

    public GetSeatLayoutService(SeatLayoutRepository seatLayoutRepository) {
        this.seatLayoutRepository = seatLayoutRepository;
    }

    @Override
    public Result get(Command command) {
        return new Result(seatLayoutRepository.findByTripId(command.tripId())
                .orElseThrow(() -> new SeatLayoutNotFoundException(command.tripId())));
    }
}
