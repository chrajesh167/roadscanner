package com.roadscanner.paymentservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** UUID-wrapping identity of a {@link PaymentTransaction} ledger line. */
public record TransactionId(UUID value) {

    public TransactionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
