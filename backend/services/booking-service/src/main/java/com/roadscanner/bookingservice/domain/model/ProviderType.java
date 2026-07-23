package com.roadscanner.bookingservice.domain.model;

import java.util.Locale;

/**
 * Identifies which external provider a {@link Booking}/{@link SeatHold} is held against — the
 * exact value {@code provider-integration-service}'s and {@code inventory-service}'s own
 * {@code ProviderType} use (an open, normalized code, not a Java {@code enum}), captured from a
 * trip's {@code ProviderMapping} at hold time
 * (docs/services/booking-service/domain-model.md's {@code Booking} entry).
 */
public record ProviderType(String code) {

    public ProviderType {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        code = code.strip().toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return code;
    }
}
