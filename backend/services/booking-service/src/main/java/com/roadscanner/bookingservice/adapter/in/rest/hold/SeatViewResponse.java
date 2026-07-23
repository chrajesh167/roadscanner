package com.roadscanner.bookingservice.adapter.in.rest.hold;

import com.roadscanner.bookingservice.domain.port.in.GetSeatSelectionView;

import java.math.BigDecimal;

public record SeatViewResponse(String seatNumber, String deck, String seatType, boolean wheelchairAccessible,
                                Integer position, String status, BigDecimal priceAmount, String priceCurrency) {

    public static SeatViewResponse from(GetSeatSelectionView.SeatView seat) {
        return new SeatViewResponse(seat.seatNumber(), seat.deck(), seat.seatType(), seat.wheelchairAccessible(),
                seat.position(), seat.status(), seat.priceAmount(), seat.priceCurrency());
    }
}
