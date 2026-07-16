package com.roadscanner.authservice.application.usecase.token;

import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * The application-layer result of starting or refreshing a session: the signed access token
 * and the raw refresh token, each with its expiry. This is the only type in the service that
 * ever carries a raw refresh token — it exists solely to hand the values outward to the REST
 * adapter, is never persisted, and never renders its secrets in {@code toString}.
 */
public record IssuedTokens(
        UserId userId,
        Role role,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {

    public IssuedTokens {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt must not be null");
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt must not be null");
    }

    @Override
    public String toString() {
        return "IssuedTokens[userId=" + userId + ", role=" + role + ", tokens redacted]";
    }
}
