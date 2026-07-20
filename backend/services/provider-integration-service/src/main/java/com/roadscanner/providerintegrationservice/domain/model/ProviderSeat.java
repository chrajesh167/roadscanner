package com.roadscanner.providerintegrationservice.domain.model;

import java.util.Objects;

/** One seat within a {@link ProviderSeatMap}. */
public record ProviderSeat(SeatNumber seatNumber, String deck, String seatType, SeatStatus status, FareAmount price) {

    public ProviderSeat {
        Objects.requireNonNull(seatNumber, "seatNumber must not be null");
        if (deck == null || deck.isBlank()) {
            throw new IllegalArgumentException("deck must not be blank");
        }
        if (seatType == null || seatType.isBlank()) {
            throw new IllegalArgumentException("seatType must not be blank");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(price, "price must not be null");
    }

    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }
}
