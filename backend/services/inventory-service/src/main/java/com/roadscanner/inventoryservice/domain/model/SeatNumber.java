package com.roadscanner.inventoryservice.domain.model;

public record SeatNumber(String value) {

    public SeatNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
