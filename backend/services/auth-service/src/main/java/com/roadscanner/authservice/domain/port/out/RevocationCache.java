package com.roadscanner.authservice.domain.port.out;

import com.roadscanner.authservice.domain.model.TokenHash;

import java.time.Instant;

/**
 * The Redis-backed fast-lookup revocation check, per
 * docs/services/auth-service/database-design.md ("Redis vs. Postgres") — a derived,
 * expendable cache of revocation state that Postgres remains authoritative for. Implemented in
 * adapter.out.cache (not built today).
 *
 * {@code markRevoked} takes an explicit expiry so the adapter can set a matching TTL on the
 * cache entry — there's no reason to remember a revocation past the point the token would have
 * expired anyway.
 */
public interface RevocationCache {

    void markRevoked(TokenHash tokenHash, Instant expiresAt);

    boolean isRevoked(TokenHash tokenHash);
}
