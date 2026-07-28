package com.roadscanner.paymentservice.domain.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * A monetary amount plus its currency. Currency is <strong>never hardcoded to one market</strong>
 * (NFR-22) — every amount carries its own currency, the same posture
 * {@code provider-integration-service}'s {@code FareAmount} and {@code booking-service}'s
 * {@code Fare} already take. A {@link Payment}'s amount is immutable once created
 * (docs/services/payment-service/domain-model.md's invariants).
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: " + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode());
        }
    }
}
