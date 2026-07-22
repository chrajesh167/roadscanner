package com.roadscanner.inventoryservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/** A trip's last-observed fare — a display/ranking snapshot, never authoritative, matching
 * {@code search-service}'s identically-purposed {@code FareSnapshot} (docs/services/inventory-service/domain-model.md
 * — "FareSnapshot"). The authoritative fare check always happens downstream, at hold/booking
 * time, against the provider or {@code operator-service}. */
public record FareAmount(BigDecimal amount, Currency currency, Instant capturedAt) {

    public FareAmount {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }
}
