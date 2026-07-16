package com.roadscanner.authservice.application.usecase.token;

import com.roadscanner.authservice.domain.port.in.RevokeSession;
import com.roadscanner.authservice.domain.port.out.RefreshTokenRepository;
import com.roadscanner.authservice.domain.port.out.RevocationCache;

/**
 * Implements {@link RevokeSession} — "Logout", exactly one session. Revocation lands in
 * Postgres first, then the Redis cache, per docs/services/auth-service/database-design.md's
 * write ordering ("writes always land in Postgres first, then populate Redis").
 */
public class RevokeSessionService implements RevokeSession {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RevocationCache revocationCache;

    public RevokeSessionService(RefreshTokenRepository refreshTokenRepository,
                                RevocationCache revocationCache) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.revocationCache = revocationCache;
    }

    @Override
    public void revoke(RevokeSessionCommand command) {
        command.token().revoke(command.now());
        refreshTokenRepository.save(command.token());
        revocationCache.markRevoked(command.token().tokenHash(), command.token().expiresAt());
    }
}
