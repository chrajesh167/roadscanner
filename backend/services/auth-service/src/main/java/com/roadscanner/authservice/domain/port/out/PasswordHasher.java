package com.roadscanner.authservice.domain.port.out;

import com.roadscanner.authservice.domain.model.PasswordHash;

/**
 * The actual password-hashing capability — an adaptive, bcrypt/argon2-class algorithm per
 * docs/services/auth-service/security-design.md. Implemented in adapter.out.security (not built
 * today: it requires a real crypto library, which is exactly what the domain layer must stay
 * independent of). {@link com.roadscanner.authservice.domain.model.Credential#authenticate}
 * takes an instance of this port as a parameter rather than the aggregate holding one as a
 * field, so the aggregate stays a plain object with no infrastructure dependency of its own.
 */
public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash hash);
}
