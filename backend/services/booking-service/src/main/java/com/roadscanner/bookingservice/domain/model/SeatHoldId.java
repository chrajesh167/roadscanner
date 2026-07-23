package com.roadscanner.bookingservice.domain.model;

import java.util.Objects;
import java.util.UUID;

public record SeatHoldId(UUID value) {

    public SeatHoldId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SeatHoldId generate() {
        return new SeatHoldId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
