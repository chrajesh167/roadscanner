package com.roadscanner.inventoryservice.domain.model;

import java.util.Objects;
import java.util.UUID;

public record StationId(UUID value) {

    public StationId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static StationId generate() {
        return new StationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
