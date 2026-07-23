package com.roadscanner.bookingservice.domain.model;

import java.util.Objects;
import java.util.UUID;

public record BookingId(UUID value) {

    public BookingId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static BookingId generate() {
        return new BookingId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
