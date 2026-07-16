package com.roadscanner.authservice.domain.port.out;

import com.roadscanner.authservice.domain.model.TokenHash;

import java.util.Objects;

/**
 * Produces the raw opaque credentials handed to clients (refresh tokens, password-reset
 * tokens) and the deterministic hash the domain stores in their place. Per
 * {@link com.roadscanner.authservice.domain.model.TokenHash}'s Javadoc, "the raw value is
 * generated and hashed by an adapter outside the domain layer" — this port is that seam,
 * implemented in adapter.out.security with a CSPRNG and a fast one-way hash.
 *
 * Deliberately distinct from {@link PasswordHasher}: passwords are low-entropy,
 * human-chosen secrets that need an adaptive, deliberately-slow algorithm; these tokens are
 * high-entropy, machine-generated values, so a fast deterministic hash (SHA-256-class) is
 * both sufficient and required — the lookup path ({@code findByTokenHash}) needs to
 * recompute the same hash from a presented raw token, which a salted adaptive hash cannot do.
 */
public interface TokenGenerator {

    GeneratedToken generate();

    /** Recomputes the stored hash for a presented raw token — the lookup-side counterpart of {@link #generate}. */
    TokenHash hashOf(String rawToken);

    /**
     * A freshly generated raw token and its hash. The raw value exists only to be handed to
     * the client (or, for reset tokens, to a future notification integration) — it is never
     * persisted; only {@code tokenHash} is.
     */
    record GeneratedToken(String rawValue, TokenHash tokenHash) {
        public GeneratedToken {
            Objects.requireNonNull(rawValue, "rawValue must not be null");
            Objects.requireNonNull(tokenHash, "tokenHash must not be null");
            if (rawValue.isBlank()) {
                throw new IllegalArgumentException("rawValue must not be blank");
            }
        }

        @Override
        public String toString() {
            return "GeneratedToken[redacted]";
        }
    }
}
