package com.roadscanner.authservice.application.usecase.token;

import com.roadscanner.authservice.domain.port.in.RevokeAllSessions;
import com.roadscanner.authservice.domain.port.out.RefreshTokenRepository;
import com.roadscanner.authservice.domain.port.out.RevocationCache;
import com.roadscanner.authservice.domain.service.RefreshTokenFamilyPolicy;

/**
 * Implements {@link RevokeAllSessions} — "Logout All Sessions", applying
 * {@link RefreshTokenFamilyPolicy} to whatever session list the caller resolved
 * (per that policy's Javadoc, fetching is the application layer's concern; the policy only
 * applies the rule). Safe to receive already-revoked tokens — revocation is idempotent.
 */
public class RevokeAllSessionsService implements RevokeAllSessions {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RevocationCache revocationCache;
    private final RefreshTokenFamilyPolicy refreshTokenFamilyPolicy;

    public RevokeAllSessionsService(RefreshTokenRepository refreshTokenRepository,
                                    RevocationCache revocationCache,
                                    RefreshTokenFamilyPolicy refreshTokenFamilyPolicy) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.revocationCache = revocationCache;
        this.refreshTokenFamilyPolicy = refreshTokenFamilyPolicy;
    }

    @Override
    public void revokeAll(RevokeAllSessionsCommand command) {
        refreshTokenFamilyPolicy.revokeFamily(command.sessions(), command.now());
        command.sessions().forEach(session -> {
            refreshTokenRepository.save(session);
            revocationCache.markRevoked(session.tokenHash(), session.expiresAt());
        });
    }
}
