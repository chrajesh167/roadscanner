package com.roadscanner.authservice.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The identifier a user logs in with — email or phone, treated as one concept throughout the
 * auth-service design docs (docs/services/auth-service/database-design.md,
 * docs/services/auth-service/api-contract.md) rather than two separate types.
 *
 * Validates a plausible email-or-phone shape in its own constructor rather than trusting that
 * adapter-level structural validation (docs/services/auth-service/validation-strategy.md) has
 * already run — a value object must never be constructible into an invalid state regardless of
 * caller, which is what makes the domain layer trustworthy in isolation.
 */
public record LoginIdentifier(String value) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{7,15}$");

    public LoginIdentifier {
        Objects.requireNonNull(value, "value must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches() && !PHONE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("value must be a plausible email or phone number");
        }
        value = trimmed;
    }

    @Override
    public String toString() {
        return value;
    }
}
