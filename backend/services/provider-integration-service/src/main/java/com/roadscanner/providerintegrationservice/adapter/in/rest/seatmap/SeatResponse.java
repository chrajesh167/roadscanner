package com.roadscanner.providerintegrationservice.adapter.in.rest.seatmap;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSeat;

import java.math.BigDecimal;

public record SeatResponse(String seatNumber, String deck, String seatType, String status, BigDecimal priceAmount,
                            String priceCurrency) {

    public static SeatResponse from(ProviderSeat seat) {
        return new SeatResponse(seat.seatNumber().value(), seat.deck(), seat.seatType(), seat.status().name(),
                seat.price().amount(), seat.price().currency().getCurrencyCode());
    }
}
