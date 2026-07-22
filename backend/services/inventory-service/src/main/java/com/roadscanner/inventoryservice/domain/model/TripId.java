package com.roadscanner.inventoryservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** The same id space {@code operator-service} and {@code search-service} use for a trip — see
 * docs/services/inventory-service/domain-model.md's {@code Trip} entry: for a first-party trip
 * this id is taken directly from the ingested {@code TripPublished} event (never minted here),
 * so a search result can be acted on without translation. For a provider-synced trip, this
 * service mints a new id (there is no upstream trip id to inherit). */
public record TripId(UUID value) {

    public TripId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static TripId generate() {
        return new TripId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
