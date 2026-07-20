package com.roadscanner.providerintegrationservice.adapter.in.rest.seatmap;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;

import java.util.List;

public record SeatMapResponse(String providerTripId, String providerType, List<SeatResponse> seats) {

    public static SeatMapResponse from(ProviderSeatMap seatMap) {
        return new SeatMapResponse(seatMap.providerTripId(), seatMap.providerType().code(),
                seatMap.seats().stream().map(SeatResponse::from).toList());
    }
}
