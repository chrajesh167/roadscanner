package com.roadscanner.providerintegrationservice.adapter.out.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.port.out.TokenCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Redis implementation of the {@link TokenCache} port — the short-TTL layer in front of
 * {@code SessionRepository}. Every operation degrades to "not cached" on failure, never to a
 * fabricated state, the same pattern {@code auth-service}'s {@code RedisRevocationCacheAdapter}
 * and {@code search-service}'s {@code RedisAvailabilityCacheAdapter} both use: Postgres remains
 * authoritative regardless of whether this cache is reachable.
 */
@Component
class RedisTokenCacheAdapter implements TokenCache {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenCacheAdapter.class);
    private static final String KEY_PREFIX = "provider:token:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    RedisTokenCacheAdapter(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ProviderToken> get(ProviderSessionId sessionId) {
        try {
            String json = redisTemplate.opsForValue().get(key(sessionId));
            if (json == null) {
                return Optional.empty();
            }
            TokenDto dto = objectMapper.readValue(json, TokenDto.class);
            return Optional.of(new ProviderToken(dto.accessToken(), dto.refreshToken(), dto.tokenType(), dto.expiresAt()));
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Token cache read failed for session {} — falling back to SessionRepository", sessionId, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(ProviderSessionId sessionId, ProviderToken token, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            TokenDto dto = new TokenDto(token.accessToken(), token.refreshToken(), token.tokenType(), token.expiresAt());
            redisTemplate.opsForValue().set(key(sessionId), objectMapper.writeValueAsString(dto), ttl);
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Token cache write failed for session {} — continuing without caching", sessionId, e);
        }
    }

    @Override
    public void evict(ProviderSessionId sessionId) {
        try {
            redisTemplate.delete(key(sessionId));
        } catch (DataAccessException e) {
            log.warn("Token cache eviction failed for session {}", sessionId, e);
        }
    }

    private String key(ProviderSessionId sessionId) {
        return KEY_PREFIX + sessionId.value();
    }

    private record TokenDto(String accessToken, String refreshToken, String tokenType, Instant expiresAt) {
    }
}
