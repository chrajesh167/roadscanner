package com.roadscanner.providerintegrationservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identity of a {@link SeatReservation} — this service's own id, distinct from whatever
 * reference the upstream provider uses internally (see {@link SeatReservation#providerBlockReference()}). */
public record ReservationId(UUID value) {

    public ReservationId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ReservationId generate() {
        return new ReservationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
