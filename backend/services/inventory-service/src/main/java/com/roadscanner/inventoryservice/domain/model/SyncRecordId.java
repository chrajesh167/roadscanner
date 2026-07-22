package com.roadscanner.inventoryservice.domain.model;

import java.util.Objects;
import java.util.UUID;

public record SyncRecordId(UUID value) {

    public SyncRecordId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SyncRecordId generate() {
        return new SyncRecordId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
