package com.roadscanner.providerintegrationservice.adapter.in.rest.seatblock;

import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;

import java.time.Instant;
import java.util.List;

public record SeatReservationResponse(String reservationId, String providerBlockReference, String providerTripId,
                                       List<String> seatNumbers, String status, Instant blockedAt, Instant expiresAt) {

    public static SeatReservationResponse from(SeatReservation reservation) {
        return new SeatReservationResponse(reservation.reservationId().toString(), reservation.providerBlockReference(),
                reservation.providerTripId(), reservation.seatNumbers().stream().map(Object::toString).toList(),
                reservation.status().name(), reservation.blockedAt(), reservation.expiresAt());
    }
}
