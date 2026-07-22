package com.roadscanner.inventoryservice.adapter.in.rest.trip;

import com.roadscanner.inventoryservice.domain.model.Seat;

/** Static shape only — deliberately no status field, matching {@code Seat}'s Javadoc. */
public record SeatResponse(String seatNumber, String deck, String seatType, boolean wheelchairAccessible, Integer position) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.seatNumber().value(), seat.deck(), seat.seatType(),
                seat.wheelchairAccessible(), seat.position());
    }
}
