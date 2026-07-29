package com.roadscanner.searchservice.location.adapter.out.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.searchservice.config.GooglePlacesProperties;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;
import com.roadscanner.searchservice.location.domain.port.out.PlaceSuggestionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Redis-backed autocomplete cache, matching {@code RedisAvailabilityCacheAdapter}'s shape and
 * {@code .claude/ARCHITECTURE_RULES.md}'s "Caching: Redis".
 *
 * <p>Redis rather than an in-process cache because Google Places is metered and billed: a shared
 * cache means one paid call serves every instance, where a per-instance cache would multiply the
 * bill by the replica count.
 *
 * <p>Redis gives TTL natively but has no per-cache size cap, so the configured
 * {@code cacheMaxSize} is enforced with a companion sorted set holding each cached query keyed by
 * insertion time. A write past the cap evicts the oldest entries. That makes the bound real
 * rather than nominal, and stops a flood of distinct one-off fragments from growing this cache
 * without limit.
 *
 * <p>Every Redis failure degrades to "not cached" instead of propagating: an unreachable cache
 * must cost a Google call, never a failed request — the same rule the availability cache follows.
 */
@Component
class RedisPlaceSuggestionCache implements PlaceSuggestionCache {

    private static final Logger log = LoggerFactory.getLogger(RedisPlaceSuggestionCache.class);
    private static final String KEY_PREFIX = "search:places:";
    /** Sorted set indexing live entries by insertion time, so the cap can evict oldest-first. */
    private static final String INDEX_KEY = "search:places:index";

    private static final TypeReference<List<CachedSuggestion>> CACHED_LIST = new TypeReference<>() {
    };

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final GooglePlacesProperties properties;

    RedisPlaceSuggestionCache(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper,
                              GooglePlacesProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<List<PlaceSuggestion>> get(String query, int limit) {
        String key = key(query, limit);
        try {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null) {
                // The value expired but its index entry outlives it — drop it so the sorted set
                // reflects what is actually cached rather than drifting upward forever.
                redisTemplate.opsForZSet().remove(INDEX_KEY, key);
                return Optional.empty();
            }
            return Optional.of(deserialize(payload));
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Place suggestion cache read failed — falling back to a live provider call", e);
            return Optional.empty();
        }
    }

    @Override
    public void put(String query, int limit, List<PlaceSuggestion> suggestions) {
        if (suggestions == null) {
            return;
        }
        String key = key(query, limit);
        try {
            String payload = objectMapper.writeValueAsString(suggestions.stream()
                    .map(CachedSuggestion::from)
                    .toList());

            redisTemplate.opsForValue().set(key, payload, properties.cacheTtl());
            redisTemplate.opsForZSet().add(INDEX_KEY, key, System.currentTimeMillis());
            evictOldestBeyondCap();
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Place suggestion cache write failed — continuing without caching this result", e);
        }
    }

    private void evictOldestBeyondCap() {
        Long size = redisTemplate.opsForZSet().zCard(INDEX_KEY);
        if (size == null || size <= properties.cacheMaxSize()) {
            return;
        }

        long excess = size - properties.cacheMaxSize();
        Set<String> oldest = redisTemplate.opsForZSet().range(INDEX_KEY, 0, excess - 1);
        if (oldest == null || oldest.isEmpty()) {
            return;
        }

        redisTemplate.delete(oldest);
        redisTemplate.opsForZSet().remove(INDEX_KEY, oldest.toArray());
    }

    private List<PlaceSuggestion> deserialize(String payload) throws JsonProcessingException {
        return objectMapper.readValue(payload, CACHED_LIST).stream()
                .map(CachedSuggestion::toDomain)
                .toList();
    }

    /**
     * Queries are cached case-insensitively and trimmed — "Hyd", "hyd " and "hyd" are the same
     * lookup to Google, so paying for three of them would be waste. The limit is part of the key
     * because a 5-result answer cannot serve a 10-result request.
     */
    private static String key(String query, int limit) {
        return KEY_PREFIX + limit + ':' + query.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The cached wire format, deliberately separate from {@link PlaceSuggestion}. The domain model
     * is free to gain fields or tighten validation without silently failing to deserialise entries
     * written by a previous deployment.
     *
     * <p>Only the provider's own answer is stored — never {@code locationId}. Curation is resolved
     * fresh on every read, so a location added to the catalogue a moment ago shows as curated
     * immediately instead of waiting out the TTL.
     */
    record CachedSuggestion(String placeId, String description, String primaryText, String secondaryText) {

        static CachedSuggestion from(PlaceSuggestion suggestion) {
            return new CachedSuggestion(
                    suggestion.googlePlaceId().value(),
                    suggestion.description(),
                    suggestion.primaryText(),
                    suggestion.secondaryText());
        }

        PlaceSuggestion toDomain() {
            return PlaceSuggestion.uncurated(new GooglePlaceId(placeId), description, primaryText, secondaryText);
        }
    }
}
