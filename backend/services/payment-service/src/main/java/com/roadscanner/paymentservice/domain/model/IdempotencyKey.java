package com.roadscanner.paymentservice.domain.model;

import java.util.Objects;

/**
 * A client-supplied key on payment initiation, or a caller-supplied key on refund initiation — the
 * basis of the idempotency strategy that makes a retried request a no-op rather than a second
 * charge or a second refund (docs/services/payment-service/domain-model.md's "Idempotency
 * Strategy"). Enforced by a unique constraint in persistence.
 */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
