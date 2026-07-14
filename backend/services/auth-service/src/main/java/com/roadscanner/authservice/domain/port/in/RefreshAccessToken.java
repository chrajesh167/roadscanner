package com.roadscanner.authservice.domain.port.in;

import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.model.RefreshTokenId;
import com.roadscanner.authservice.domain.model.TokenHash;

import java.time.Instant;
import java.util.Objects;

/**
 * Exchanges a refresh token for a new one — "Refresh". The command carries the already-fetched
 * {@code currentToken} (the application layer resolves it from the presented raw token's hash
 * via {@link com.roadscanner.authservice.domain.port.out.RefreshTokenRepository} before calling
 * this use case, since that lookup is I/O the domain layer doesn't perform itself); the actual
 * rotation and reuse-detection logic lives in {@code RefreshToken.rotate(...)}, which this
 * use-case's implementation delegates to.
 */
public interface RefreshAccessToken {

    RefreshResult refresh(RefreshAccessTokenCommand command);

    record RefreshAccessTokenCommand(RefreshToken currentToken, RefreshTokenId newTokenId,
                                      TokenHash newTokenHash, Instant now, Instant newExpiresAt) {
        public RefreshAccessTokenCommand {
            Objects.requireNonNull(currentToken, "currentToken must not be null");
            Objects.requireNonNull(newTokenId, "newTokenId must not be null");
            Objects.requireNonNull(newTokenHash, "newTokenHash must not be null");
            Objects.requireNonNull(now, "now must not be null");
            Objects.requireNonNull(newExpiresAt, "newExpiresAt must not be null");
        }
    }

    record RefreshResult(RefreshToken newRefreshToken) {
        public RefreshResult {
            Objects.requireNonNull(newRefreshToken, "newRefreshToken must not be null");
        }
    }
}
