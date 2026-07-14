package com.roadscanner.authservice.domain.port.out;

import com.roadscanner.authservice.domain.model.PasswordHash;

/**
 * A deliberately trivial {@link PasswordHasher} test double — "hashing" is just prefixing, and
 * matching is a string comparison. Domain tests must never depend on a real crypto library
 * (bcrypt/argon2), since that dependency belongs to the adapter layer, not the domain — this
 * double is what makes {@code Credential.authenticate} testable in isolation.
 */
public final class StubPasswordHasher implements PasswordHasher {

    private static final String ALGORITHM_ID = "stub";

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash("hashed:" + rawPassword, ALGORITHM_ID);
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        return hash.value().equals("hashed:" + rawPassword);
    }
}
