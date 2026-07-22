package com.roadscanner.inventoryservice.domain.model;

import java.util.Objects;
import java.util.UUID;

public record CityId(UUID value) {

    public CityId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static CityId generate() {
        return new CityId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
