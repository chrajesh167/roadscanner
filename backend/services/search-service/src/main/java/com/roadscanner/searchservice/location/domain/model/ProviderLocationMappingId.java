package com.roadscanner.searchservice.location.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identity of a {@link ProviderLocationMapping} row. Internal to this module — no public contract
 * exposes it, since callers address a mapping by (provider, location) or by provider id. */
public record ProviderLocationMappingId(UUID value) {

    public ProviderLocationMappingId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ProviderLocationMappingId generate() {
        return new ProviderLocationMappingId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
