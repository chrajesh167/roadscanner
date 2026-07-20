package com.roadscanner.providerintegrationservice.domain.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/** A fare quoted by a provider, in the provider's own currency — this service never converts
 * currency, it passes through exactly what the provider quoted. */
public record FareAmount(BigDecimal amount, Currency currency) {

    public FareAmount {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }
}
