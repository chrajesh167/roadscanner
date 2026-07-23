package com.roadscanner.bookingservice.domain.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/** Captured from {@code inventory-service}'s {@code FareSnapshot} at hold time and frozen —
 * deliberately never refreshed thereafter (docs/services/booking-service/data-ownership.md's
 * "The One Deliberate Non-Refresh"). A traveler's charged amount is what was quoted when the seat
 * was held, not whatever the catalog says later. */
public record Fare(BigDecimal amount, Currency currency) {

    public Fare {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }
}
