package com.roadscanner.searchservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * The canonical identity of a trip, shared by value with {@code operator-service} and
 * {@code inventory-service} — see docs/services/search-service/domain-model.md: "the same id
 * inventory-service/operator-service use, so a search result can be acted on... without
 * translation." {@code search-service} never generates one itself; every instance is
 * constructed from an upstream event's own trip identifier.
 */
public record TripId(UUID value) {

    public TripId {
        Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
