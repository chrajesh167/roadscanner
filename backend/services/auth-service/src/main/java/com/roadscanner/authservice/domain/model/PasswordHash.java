package com.roadscanner.authservice.domain.model;

import java.util.Objects;

/**
 * A hashed password — never the raw value. There is deliberately no constructor path from a raw
 * String password; instances are only ever produced by a
 * {@link com.roadscanner.authservice.domain.port.out.PasswordHasher} adapter implementation
 * (bcrypt/argon2-class, per docs/services/auth-service/security-design.md), which lives outside
 * the domain layer. The domain never sees, stores, or compares a raw password itself.
 *
 * {@code algorithmId} identifies the hashing algorithm/cost-factor version that produced
 * {@code value}, so {@link com.roadscanner.authservice.domain.service.PasswordHashingPolicy}
 * can detect a hash that predates the platform's current baseline and needs upgrading — see
 * that class's Javadoc.
 */
public record PasswordHash(String value, String algorithmId) {

    public PasswordHash {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(algorithmId, "algorithmId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (algorithmId.isBlank()) {
            throw new IllegalArgumentException("algorithmId must not be blank");
        }
    }

    @Override
    public String toString() {
        // Never render the actual hash value in logs/toString, even though it's not the raw
        // password — no reason to make an attacker's job easier if a log line is ever exposed.
        return "PasswordHash[algorithmId=" + algorithmId + "]";
    }
}
