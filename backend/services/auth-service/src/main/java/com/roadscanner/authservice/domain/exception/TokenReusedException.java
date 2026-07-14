package com.roadscanner.authservice.domain.exception;

import com.roadscanner.authservice.domain.model.RefreshTokenId;

/**
 * A refresh token that was already rotated (or otherwise revoked) was presented again — the
 * reuse-detection security signal from docs/services/auth-service/security-design.md. Thrown by
 * {@code RefreshToken.rotate}. The application layer (next implementation phase) is expected to
 * respond by revoking the entire token family via
 * {@link com.roadscanner.authservice.domain.service.RefreshTokenFamilyPolicy}, not just this
 * one token.
 */
public final class TokenReusedException extends AuthServiceException {

    private final RefreshTokenId tokenId;

    public TokenReusedException(RefreshTokenId tokenId) {
        super("Refresh token reuse detected: " + tokenId);
        this.tokenId = tokenId;
    }

    public RefreshTokenId tokenId() {
        return tokenId;
    }
}
