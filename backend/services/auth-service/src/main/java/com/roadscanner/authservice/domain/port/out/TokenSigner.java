package com.roadscanner.authservice.domain.port.out;

import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.UserId;

import java.time.Instant;

/**
 * Produces the signed JWT access token string handed back to a client. Implemented in
 * adapter.out.security using asymmetric (RS256/ES256) signing (not built today — see
 * docs/services/auth-service/security-design.md).
 *
 * Deliberately returns a raw {@code String}, not a domain type: the signed JWT is a
 * serialization artifact of this port's implementation, not a domain concept the rest of the
 * domain layer needs to reason about. Nothing in domain.model depends on this port or ever
 * holds a signed token — the same "domain never holds the raw secret" principle applied to
 * {@link com.roadscanner.authservice.domain.model.PasswordHash} and
 * {@link com.roadscanner.authservice.domain.model.TokenHash}, applied here to the access token.
 */
public interface TokenSigner {

    String sign(UserId userId, Role role, Instant issuedAt, Instant expiresAt);
}
