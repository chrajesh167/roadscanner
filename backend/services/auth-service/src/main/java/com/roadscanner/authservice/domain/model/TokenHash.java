package com.roadscanner.authservice.domain.model;

import java.util.Objects;

/**
 * A hash of a refresh token or password-reset token — never the raw value. Same rationale as
 * {@link PasswordHash}: if the database were ever compromised, a stored raw token is
 * immediately usable by an attacker, while a hash requires the attacker to already possess the
 * original (see docs/services/auth-service/database-design.md). The raw value is generated and
 * hashed by an adapter outside the domain layer; the domain only ever holds the hash.
 */
public record TokenHash(String value) {

    public TokenHash {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    @Override
    public String toString() {
        return "TokenHash[redacted]";
    }
}
