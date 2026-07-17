package com.roadscanner.searchservice.domain.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * The fare {@code search-service} last observed for a trip via an {@code operator-service}
 * event — explicitly a snapshot, not a live price feed
 * (docs/services/search-service/domain-model.md): "if operator-service changes a fare between
 * this snapshot and a traveler's actual hold/booking attempt, the authoritative fare check
 * happens where it always does, downstream at hold/booking time." Currency is carried
 * explicitly, never assumed, per docs/requirements/non-functional-requirements.md NFR-22
 * (locale/currency must not be hardcoded to a single market).
 */
public record FareSnapshot(BigDecimal amount, Currency currency) {

    public FareSnapshot {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount;
    }
}
