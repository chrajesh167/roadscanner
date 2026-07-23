package com.roadscanner.bookingservice.adapter.in.rest.hold;

import com.roadscanner.bookingservice.domain.port.in.GetSeatSelectionView;

import java.util.List;

public record SeatSelectionViewResponse(List<SeatViewResponse> seats) {

    public static SeatSelectionViewResponse from(GetSeatSelectionView.Result result) {
        return new SeatSelectionViewResponse(result.seats().stream().map(SeatViewResponse::from).toList());
    }
}
