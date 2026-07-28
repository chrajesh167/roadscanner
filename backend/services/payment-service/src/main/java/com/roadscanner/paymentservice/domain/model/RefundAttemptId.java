package com.roadscanner.paymentservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** UUID-wrapping identity of a {@link RefundAttempt}. */
public record RefundAttemptId(UUID value) {

    public RefundAttemptId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static RefundAttemptId generate() {
        return new RefundAttemptId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
