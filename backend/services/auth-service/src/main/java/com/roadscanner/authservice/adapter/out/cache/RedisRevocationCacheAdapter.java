package com.roadscanner.authservice.adapter.out.cache;

import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.port.out.RevocationCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Redis implementation of the {@link RevocationCache} port. Per
 * docs/services/auth-service/database-design.md ("Redis is always a derived, expendable
 * copy"), Postgres remains authoritative for revocation state — so every Redis failure here
 * degrades to "cache miss" (log a warning, answer as if not cached) instead of failing the
 * request. A flushed or unreachable Redis makes revocation checks slower, never incorrect.
 *
 * Entries carry a TTL matching the token's own expiry — there is no reason to remember a
 * revocation past the point the token would have expired anyway (see the port's Javadoc).
 */
@Component
class RedisRevocationCacheAdapter implements RevocationCache {

    private static final Logger log = LoggerFactory.getLogger(RedisRevocationCacheAdapter.class);
    private static final String KEY_PREFIX = "auth:revoked-token:";
    private static final String REVOKED_MARKER = "1";

    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clock;

    RedisRevocationCacheAdapter(RedisTemplate<String, String> redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Override
    public void markRevoked(TokenHash tokenHash, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(clock), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(tokenHash), REVOKED_MARKER, ttl);
        } catch (DataAccessException e) {
            log.warn("Revocation cache write failed — Postgres remains authoritative, continuing", e);
        }
    }

    @Override
    public boolean isRevoked(TokenHash tokenHash) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(tokenHash)));
        } catch (DataAccessException e) {
            log.warn("Revocation cache read failed — falling back to Postgres as source of truth", e);
            return false;
        }
    }

    private String key(TokenHash tokenHash) {
        return KEY_PREFIX + tokenHash.value();
    }
}
