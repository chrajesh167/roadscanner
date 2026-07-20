package com.roadscanner.providerintegrationservice.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/** The provider-agnostic search input — the same three fields every provider's search API needs
 * (origin, destination, travel date), translated into that provider's own request shape by its
 * adapter's mapper (e.g. {@code FlixBusMapper}). */
public record SearchCriteria(String origin, String destination, LocalDate travelDate) {

    public SearchCriteria {
        if (origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("origin must not be blank");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination must not be blank");
        }
        if (origin.equalsIgnoreCase(destination)) {
            throw new IllegalArgumentException("origin and destination must differ");
        }
        Objects.requireNonNull(travelDate, "travelDate must not be null");
    }
}
