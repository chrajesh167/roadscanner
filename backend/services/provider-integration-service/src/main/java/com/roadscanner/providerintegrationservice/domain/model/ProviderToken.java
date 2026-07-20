package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** The credential material returned by a provider's authentication call. {@code refreshToken}
 * is optional — not every provider issues one; a provider without one is simply re-authenticated
 * from scratch on expiry instead of refreshed (see {@code RefreshAccessTokenService}-equivalent
 * orchestration in the {@code RefreshSession} use case). */
public record ProviderToken(String accessToken, String refreshToken, String tokenType, Instant expiresAt) {

    public ProviderToken {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        if (tokenType == null || tokenType.isBlank()) {
            throw new IllegalArgumentException("tokenType must not be blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public Optional<String> refreshTokenIfPresent() {
        return Optional.ofNullable(refreshToken);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
