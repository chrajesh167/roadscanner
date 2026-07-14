package com.roadscanner.authservice.domain.service;

import com.roadscanner.authservice.domain.model.RefreshToken;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Revokes an entire refresh-token rotation chain at once — the response to reuse detection
 * (docs/services/auth-service/security-design.md: "the entire token family... is revoked
 * immediately, forcing re-authentication") and the mechanism behind the user-facing
 * "logout everywhere" capability. Both share this policy rather than duplicating the loop,
 * even though they're triggered by different actors (a security response vs. a deliberate user
 * action) — the actual revocation behavior is identical.
 *
 * Fetching the family (walking "replaces" pointers, or querying by user id) is a repository
 * concern for the application layer; this policy only applies the revocation rule to whatever
 * list it's given. Safe to call with a list that already contains revoked tokens —
 * {@link RefreshToken#revoke} is itself idempotent.
 */
public final class RefreshTokenFamilyPolicy {

    public void revokeFamily(List<RefreshToken> family, Instant now) {
        Objects.requireNonNull(family, "family must not be null");
        Objects.requireNonNull(now, "now must not be null");
        family.forEach(token -> token.revoke(now));
    }
}
