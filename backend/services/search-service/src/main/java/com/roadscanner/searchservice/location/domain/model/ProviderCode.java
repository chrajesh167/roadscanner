package com.roadscanner.searchservice.location.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Names a supply provider ({@code FLIXBUS}, {@code REDBUS}, …) inside a
 * {@link ProviderLocationMapping}.
 *
 * <p>An open string rather than an enum, on purpose: onboarding a provider is an operational
 * event, not a code change, and {@code provider-integration-service} already owns the closed
 * {@code ProviderType} vocabulary. Duplicating that enum here would create two sources of truth
 * that drift. Normalised to upper case so {@code flixbus} and {@code FlixBus} cannot become two
 * distinct providers in the same table.
 */
public record ProviderCode(String value) {

    private static final int MAX_LENGTH = 50;

    public ProviderCode {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("provider must be at most " + MAX_LENGTH + " characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
