package com.roadscanner.authservice.adapter.in.rest.token;

import com.roadscanner.authservice.application.usecase.token.IssuedTokens;

import java.time.Instant;

/**
 * The client-facing shape of a started or refreshed session — shared by register, login, and
 * refresh, which all hand back the same thing per docs/services/auth-service/api-contract.md.
 * {@code tokenType} is the standard OAuth-style hint that the access token goes in an
 * {@code Authorization: Bearer} header.
 */
public record AuthTokensResponse(
        String userId,
        String role,
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {

    public static AuthTokensResponse from(IssuedTokens tokens) {
        return new AuthTokensResponse(
                tokens.userId().toString(),
                tokens.role().name(),
                "Bearer",
                tokens.accessToken(),
                tokens.accessTokenExpiresAt(),
                tokens.refreshToken(),
                tokens.refreshTokenExpiresAt()
        );
    }
}
