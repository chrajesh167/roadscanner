package com.roadscanner.inventoryservice.adapter.in.rest.trip;

import com.roadscanner.inventoryservice.domain.model.SeatLayout;

import java.util.List;

public record SeatLayoutResponse(String tripId, List<SeatResponse> seats) {

    public static SeatLayoutResponse from(SeatLayout seatLayout) {
        return new SeatLayoutResponse(seatLayout.tripId().toString(),
                seatLayout.seats().stream().map(SeatResponse::from).toList());
    }
}
