package com.roadscanner.inventoryservice.domain.model;

import java.util.Objects;

/**
 * One seat's **static** shape within a {@link SeatLayout} — numbering, deck, type, physical
 * position, wheelchair accessibility. Per docs/services/inventory-service/domain-model.md and
 * the request that authorized this implementation: this type carries no status field of any
 * kind, and must never be extended with one — booked/held/available is
 * {@code provider-integration-service}'s concern entirely, computed live, never stored here.
 */
public record Seat(SeatNumber seatNumber, String deck, String seatType, boolean wheelchairAccessible, Integer position) {

    public Seat {
        Objects.requireNonNull(seatNumber, "seatNumber must not be null");
        if (deck == null || deck.isBlank()) {
            throw new IllegalArgumentException("deck must not be blank");
        }
        if (seatType == null || seatType.isBlank()) {
            throw new IllegalArgumentException("seatType must not be blank");
        }
    }
}
