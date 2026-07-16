package com.roadscanner.authservice.application.usecase.token;

import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.port.in.RefreshAccessToken;
import com.roadscanner.authservice.domain.port.out.RefreshTokenRepository;
import com.roadscanner.authservice.domain.port.out.RevocationCache;

/**
 * Implements {@link RefreshAccessToken}: delegates the rotation/reuse-detection rule to
 * {@code RefreshToken.rotate(...)} (the single enforcement point, per that method's Javadoc),
 * then persists both links of the chain and marks the superseded token revoked in the cache.
 * Raw-token resolution and the reuse-response (family revocation) live in
 * {@link TokenRefresher}, which owns the transaction this runs inside.
 */
public class RefreshAccessTokenService implements RefreshAccessToken {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RevocationCache revocationCache;

    public RefreshAccessTokenService(RefreshTokenRepository refreshTokenRepository,
                                     RevocationCache revocationCache) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.revocationCache = revocationCache;
    }

    @Override
    public RefreshResult refresh(RefreshAccessTokenCommand command) {
        RefreshToken current = command.currentToken();
        RefreshToken rotated = current.rotate(
                command.newTokenId(), command.newTokenHash(), command.now(), command.newExpiresAt());

        refreshTokenRepository.save(current);
        refreshTokenRepository.save(rotated);
        revocationCache.markRevoked(current.tokenHash(), current.expiresAt());
        return new RefreshResult(rotated);
    }
}
