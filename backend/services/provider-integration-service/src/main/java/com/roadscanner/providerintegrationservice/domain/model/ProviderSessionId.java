package com.roadscanner.providerintegrationservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identity of a {@link ProviderSession}. Callers pass this back on every subsequent call
 * (search, seat map, block, confirm, ticket) so the service can resolve the session's provider
 * and token without the caller needing to know either. */
public record ProviderSessionId(UUID value) {

    public ProviderSessionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ProviderSessionId generate() {
        return new ProviderSessionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
