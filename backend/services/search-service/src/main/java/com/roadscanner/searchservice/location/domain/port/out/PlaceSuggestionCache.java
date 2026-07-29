package com.roadscanner.searchservice.location.domain.port.out;

import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;

import java.util.List;
import java.util.Optional;

/**
 * Caches autocomplete results. Autocomplete fires on every keystroke against a metered, paid API,
 * so the cache is a cost control as much as a latency one.
 *
 * <p>The contract that matters is what must <em>not</em> be cached: only successful lookups are
 * storable. A provider outage must never be frozen into the cache for the whole TTL, because that
 * would turn a thirty-second blip into minutes of empty dropdowns long after the provider
 * recovered. The use case enforces this by only calling {@link #put} on a successful result;
 * implementations must not cache on the failure path either.
 *
 * <p>A cache miss and a cached-empty-result are deliberately distinguishable: {@link #get} returns
 * an empty {@code Optional} for "not cached" and an {@code Optional} of an empty list for
 * "the provider genuinely has no match for this fragment", which is a useful thing to remember.
 */
public interface PlaceSuggestionCache {

    /**
     * @return empty when nothing is cached for this query; an {@code Optional} wrapping the cached
     *         list — possibly itself empty — when there is a hit
     */
    Optional<List<PlaceSuggestion>> get(String query, int limit);

    /** Stores a successful lookup. Never called with the outcome of a failed one. */
    void put(String query, int limit, List<PlaceSuggestion> suggestions);
}
