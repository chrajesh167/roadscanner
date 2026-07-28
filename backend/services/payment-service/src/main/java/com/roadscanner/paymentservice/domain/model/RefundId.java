package com.roadscanner.paymentservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** UUID-wrapping identity of a {@link Refund}. */
public record RefundId(UUID value) {

    public RefundId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static RefundId generate() {
        return new RefundId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
