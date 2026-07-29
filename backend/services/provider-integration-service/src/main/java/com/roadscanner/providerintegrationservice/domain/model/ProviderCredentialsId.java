package com.roadscanner.providerintegrationservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identity of a {@link ProviderCredentials} row. Internal to this service — no public contract
 * exposes it, since credentials are always addressed by their provider. */
public record ProviderCredentialsId(UUID value) {

    public ProviderCredentialsId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ProviderCredentialsId generate() {
        return new ProviderCredentialsId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
