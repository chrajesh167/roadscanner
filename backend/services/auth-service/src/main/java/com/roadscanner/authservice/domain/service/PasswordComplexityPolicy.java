package com.roadscanner.authservice.domain.service;

import com.roadscanner.authservice.domain.exception.PasswordPolicyViolationException;

import java.util.Objects;

/**
 * The platform's password complexity rule, applied identically at registration and at
 * password-reset confirmation (docs/services/auth-service/validation-strategy.md) — centralized
 * here so the two call sites can never quietly drift apart.
 *
 * Operates on the raw password string, which is why this must run <em>before</em> hashing
 * (complexity is a property of the human-readable password, not the hash) and why the raw value
 * is never retained by this class — it's validated and discarded in the same call.
 *
 * Thresholds are constructor parameters, not hardcoded constants: exact complexity rules are
 * expected to be an operationally-tunable baseline (docs/services/auth-service/security-design.md
 * makes the same point about password hashing cost factors), so the domain layer enforces the
 * *shape* of the rule without pinning its exact values. {@link #standard()} is today's baseline.
 */
public final class PasswordComplexityPolicy {

    private final int minLength;

    private PasswordComplexityPolicy(int minLength) {
        if (minLength < 1) {
            throw new IllegalArgumentException("minLength must be positive");
        }
        this.minLength = minLength;
    }

    public static PasswordComplexityPolicy of(int minLength) {
        return new PasswordComplexityPolicy(minLength);
    }

    /** The platform's current baseline: at least 12 characters, at least one letter and one digit. */
    public static PasswordComplexityPolicy standard() {
        return new PasswordComplexityPolicy(12);
    }

    /**
     * @throws PasswordPolicyViolationException if {@code rawPassword} does not meet this policy.
     *         The message safely describes which rule failed — see that exception's Javadoc.
     */
    public void validate(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        if (rawPassword.isBlank()) {
            throw new PasswordPolicyViolationException("Password must not be blank");
        }
        if (rawPassword.length() < minLength) {
            throw new PasswordPolicyViolationException("Password must be at least " + minLength + " characters");
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new PasswordPolicyViolationException("Password must contain at least one letter and one digit");
        }
    }

    public int minLength() {
        return minLength;
    }
}
