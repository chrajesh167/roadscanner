package com.roadscanner.bookingservice.adapter.in.rest.hold;

import com.roadscanner.bookingservice.domain.port.in.HoldSeats;

import java.time.Instant;
import java.util.List;

public record HoldSeatsResponse(String seatHoldId, List<String> seatNumbers, Instant expiresAt) {

    public static HoldSeatsResponse from(HoldSeats.Result result) {
        return new HoldSeatsResponse(result.seatHoldId().toString(), result.seatNumbers(), result.expiresAt());
    }
}
