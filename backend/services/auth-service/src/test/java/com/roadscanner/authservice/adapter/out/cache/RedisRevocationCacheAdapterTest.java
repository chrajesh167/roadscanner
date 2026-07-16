package com.roadscanner.authservice.adapter.out.cache;

import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.port.out.RevocationCache;
import com.roadscanner.authservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the revocation cache against a real Redis (Testcontainers). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RedisRevocationCacheAdapterTest {

    @Autowired
    private RevocationCache revocationCache;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private TokenHash randomHash() {
        return new TokenHash("test-hash-" + UUID.randomUUID());
    }

    @Test
    void marksAndReportsRevocation() {
        TokenHash hash = randomHash();
        assertThat(revocationCache.isRevoked(hash)).isFalse();

        revocationCache.markRevoked(hash, Instant.now().plus(Duration.ofHours(1)));

        assertThat(revocationCache.isRevoked(hash)).isTrue();
    }

    @Test
    void entryCarriesATtlMatchingTheTokenExpiry() {
        TokenHash hash = randomHash();
        revocationCache.markRevoked(hash, Instant.now().plus(Duration.ofMinutes(10)));

        Long ttlSeconds = redisTemplate.getExpire("auth:revoked-token:" + hash.value());
        assertThat(ttlSeconds).isBetween(1L, 600L);
    }

    @Test
    void expiredTokensAreNeverCached() {
        // No reason to remember a revocation past the token's own expiry (port Javadoc) —
        // and Redis would reject a non-positive TTL anyway.
        TokenHash hash = randomHash();
        revocationCache.markRevoked(hash, Instant.now().minusSeconds(5));

        assertThat(revocationCache.isRevoked(hash)).isFalse();
    }
}
