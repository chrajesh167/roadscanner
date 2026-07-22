package com.roadscanner.inventoryservice.domain.model;

import java.util.Objects;
import java.util.UUID;

public record RouteId(UUID value) {

    public RouteId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static RouteId generate() {
        return new RouteId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
