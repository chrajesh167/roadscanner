package com.roadscanner.authservice.domain.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Enforces the one structural invariant docs/services/auth-service/security-design.md draws
 * between the platform's two token lifetimes: the access token TTL must be strictly shorter
 * than the refresh token TTL ("a shorter access token reduces the exposure window... favor
 * short-lived access tokens plus revocable refresh tokens"). Exact TTL values are an
 * environment-specific configuration concern (sourced from Spring config in a later
 * implementation phase) — this class only enforces the relationship between them, wherever the
 * values come from.
 */
public final class TokenExpiryPolicy {

    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    private TokenExpiryPolicy(Duration accessTokenTtl, Duration refreshTokenTtl) {
        Objects.requireNonNull(accessTokenTtl, "accessTokenTtl must not be null");
        Objects.requireNonNull(refreshTokenTtl, "refreshTokenTtl must not be null");
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("accessTokenTtl must be positive");
        }
        if (refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            throw new IllegalArgumentException("refreshTokenTtl must be positive");
        }
        if (accessTokenTtl.compareTo(refreshTokenTtl) >= 0) {
            throw new IllegalArgumentException("accessTokenTtl must be strictly shorter than refreshTokenTtl");
        }
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public static TokenExpiryPolicy of(Duration accessTokenTtl, Duration refreshTokenTtl) {
        return new TokenExpiryPolicy(accessTokenTtl, refreshTokenTtl);
    }

    public Instant accessTokenExpiry(Instant issuedAt) {
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        return issuedAt.plus(accessTokenTtl);
    }

    public Instant refreshTokenExpiry(Instant issuedAt) {
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        return issuedAt.plus(refreshTokenTtl);
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }
}
