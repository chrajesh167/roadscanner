package com.roadscanner.paymentservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** UUID-wrapping identity of a {@link Payment} — matching every other identifier on this platform
 * ({@code BookingId}, {@code TripId}, ...). Assigned by {@code payment-service}, never a client.
 * Its {@link #toString()} is the opaque {@code paymentReference} other services correlate against
 * (docs/services/payment-service/domain-model.md). */
public record PaymentId(UUID value) {

    public PaymentId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PaymentId generate() {
        return new PaymentId(UUID.randomUUID());
    }

    public static PaymentId fromString(String value) {
        return new PaymentId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
