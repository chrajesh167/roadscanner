package com.roadscanner.searchservice.adapter.out.cache;

import com.roadscanner.searchservice.config.SearchProperties;
import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.out.AvailabilityCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis implementation of the {@link AvailabilityCache} port — the short-TTL layer in front of
 * {@code inventory-service} (docs/architecture/high-level-design.md §7,
 * docs/services/search-service/boundaries.md). Only ever stores a known seat count as a plain
 * string (never {@link AvailabilityStatus#unknown()} — see
 * {@code AvailabilityOverlay}'s Javadoc for why an unknown result is never cached in the first
 * place, which is what keeps this adapter's serialization trivial).
 *
 * Per this port's Javadoc, a cache failure degrades to "not cached," never to "known
 * unavailable" or any other invented state — every Redis operation here catches
 * {@link DataAccessException}, logs a warning, and falls through, the identical degrade
 * pattern {@code auth-service}'s {@code RedisRevocationCacheAdapter} uses for the same reason:
 * Postgres (there) / {@code inventory-service} (here) remains authoritative regardless of
 * whether this cache is reachable.
 */
@Component
class RedisAvailabilityCacheAdapter implements AvailabilityCache {

    private static final Logger log = LoggerFactory.getLogger(RedisAvailabilityCacheAdapter.class);
    private static final String KEY_PREFIX = "search:availability:";

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration cacheTtl;

    RedisAvailabilityCacheAdapter(RedisTemplate<String, String> redisTemplate, SearchProperties properties) {
        this.redisTemplate = redisTemplate;
        this.cacheTtl = properties.availability().cacheTtl();
    }

    @Override
    public Optional<AvailabilityStatus> get(TripId tripId) {
        try {
            String value = redisTemplate.opsForValue().get(key(tripId));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(AvailabilityStatus.of(Integer.parseInt(value)));
        } catch (DataAccessException e) {
            log.warn("Availability cache read failed — falling back to a live inventory-service call", e);
            return Optional.empty();
        }
    }

    @Override
    public void put(TripId tripId, AvailabilityStatus status) {
        if (!status.isKnown()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(tripId), String.valueOf(status.seatsAvailable().getAsInt()), cacheTtl);
        } catch (DataAccessException e) {
            log.warn("Availability cache write failed — continuing without caching this result", e);
        }
    }

    private String key(TripId tripId) {
        return KEY_PREFIX + tripId.value();
    }
}
