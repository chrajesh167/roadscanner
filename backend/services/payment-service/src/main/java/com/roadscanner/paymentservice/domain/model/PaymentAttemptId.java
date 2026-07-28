package com.roadscanner.paymentservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** UUID-wrapping identity of a {@link PaymentAttempt}. */
public record PaymentAttemptId(UUID value) {

    public PaymentAttemptId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PaymentAttemptId generate() {
        return new PaymentAttemptId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
