package com.roadscanner.providerintegrationservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identity of a {@link ProviderBooking} — this service's own id, distinct from the provider's
 * order reference (see {@link ProviderBooking#providerOrderReference()}). */
public record ProviderBookingId(UUID value) {

    public ProviderBookingId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ProviderBookingId generate() {
        return new ProviderBookingId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
