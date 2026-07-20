package com.roadscanner.providerintegrationservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identity of a {@link Provider} configuration row. */
public record ProviderId(UUID value) {

    public ProviderId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ProviderId generate() {
        return new ProviderId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
