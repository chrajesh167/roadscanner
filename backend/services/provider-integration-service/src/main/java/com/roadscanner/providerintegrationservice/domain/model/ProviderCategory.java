package com.roadscanner.providerintegrationservice.domain.model;

import java.util.Locale;

/**
 * The transport vertical a provider serves — {@code BUS}, {@code RAIL}, {@code AIRLINE}, …
 *
 * <p>A value object rather than an enum, for the same reason {@link ProviderType} is: onboarding a
 * vertical must not require recompiling this class or any {@code switch} over it. Phase 1 is
 * bus-only, so every seeded row is {@code BUS}; the column exists now so that adding rail is a row,
 * not a migration plus a code change.
 *
 * <p>Distinct from {@link ProviderType}, which identifies <em>which</em> provider (FLIXBUS). Two
 * providers can share a category; a provider has exactly one.
 */
public record ProviderCategory(String code) {

    public static final ProviderCategory BUS = new ProviderCategory("BUS");
    public static final ProviderCategory RAIL = new ProviderCategory("RAIL");
    public static final ProviderCategory AIRLINE = new ProviderCategory("AIRLINE");

    private static final int MAX_LENGTH = 50;

    public ProviderCategory {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        code = code.strip().toUpperCase(Locale.ROOT);
        if (code.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("code must be at most " + MAX_LENGTH + " characters");
        }
    }

    @Override
    public String toString() {
        return code;
    }
}
