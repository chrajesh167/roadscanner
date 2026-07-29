package com.roadscanner.searchservice.location.testsupport;

import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;
import com.roadscanner.searchservice.location.domain.port.out.PlaceSuggestionCache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link PlaceSuggestionCache} for application-layer tests.
 *
 * <p>Reproduces the adapter's contract — case-insensitive keys, limit as part of the key,
 * cached-empty distinguishable from a miss — but not its Redis mechanics or its size cap. Those
 * are covered against a real Redis by {@code RedisPlaceSuggestionCacheTest}, the same division
 * {@code InMemoryLocationRepository} keeps with its Testcontainers counterpart.
 */
public final class InMemoryPlaceSuggestionCache implements PlaceSuggestionCache {

    private final Map<String, List<PlaceSuggestion>> stored = new LinkedHashMap<>();
    private final List<String> writes = new ArrayList<>();

    @Override
    public Optional<List<PlaceSuggestion>> get(String query, int limit) {
        return Optional.ofNullable(stored.get(key(query, limit)));
    }

    @Override
    public void put(String query, int limit, List<PlaceSuggestion> suggestions) {
        String key = key(query, limit);
        stored.put(key, List.copyOf(suggestions));
        writes.add(key);
    }

    public void seed(String query, int limit, List<PlaceSuggestion> suggestions) {
        stored.put(key(query, limit), List.copyOf(suggestions));
    }

    /** Every key written, in order — lets a test assert that a failure wrote nothing at all. */
    public List<String> writes() {
        return List.copyOf(writes);
    }

    public int size() {
        return stored.size();
    }

    private static String key(String query, int limit) {
        return limit + ":" + query.trim().toLowerCase(Locale.ROOT);
    }
}
