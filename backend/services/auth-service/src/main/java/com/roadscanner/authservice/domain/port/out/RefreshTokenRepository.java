package com.roadscanner.authservice.domain.port.out;

import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link RefreshToken}. {@code findByUserId} backs both "logout everywhere"
 * and reuse-detection family lookups (docs/services/auth-service/database-design.md's indexing
 * note: "an index on user identifier, to support 'revoke all sessions for this user'").
 * Implemented by a Postgres/JPA adapter in adapter.out.persistence (not built today).
 */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(TokenHash tokenHash);

    List<RefreshToken> findByUserId(UserId userId);

    RefreshToken save(RefreshToken refreshToken);
}
