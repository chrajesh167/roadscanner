package com.roadscanner.authservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies one {@link RefreshToken} — one link in a rotation chain (session). Distinct from
 * {@link TokenHash}: this is a stable identifier for the row/entity itself, used to reference
 * "the token this one replaces"; the hash is what's checked against a presented raw token.
 */
public record RefreshTokenId(UUID value) {

    public RefreshTokenId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static RefreshTokenId generate() {
        return new RefreshTokenId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
