package com.roadscanner.searchservice.location.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * The canonical platform-wide identity of a place. This is the <em>only</em> location identifier
 * any other service, client, or public contract is permitted to hold — provider identifiers stay
 * behind {@link ProviderLocationMapping} (docs/architecture/service-boundaries.md's rule that a
 * provider's vocabulary never crosses a service boundary).
 *
 * <p>Minted by this service, never inherited from a provider, so a location's identity survives
 * a provider being swapped out or dropped entirely.
 */
public record LocationId(UUID value) {

    public LocationId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static LocationId generate() {
        return new LocationId(UUID.randomUUID());
    }

    public static LocationId of(String value) {
        Objects.requireNonNull(value, "value must not be null");
        return new LocationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
