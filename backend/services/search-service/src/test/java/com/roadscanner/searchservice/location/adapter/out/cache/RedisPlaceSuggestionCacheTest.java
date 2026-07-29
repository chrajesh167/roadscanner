package com.roadscanner.searchservice.location.adapter.out.cache;

import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;
import com.roadscanner.searchservice.location.domain.port.out.PlaceSuggestionCache;
import com.roadscanner.searchservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cache against a real Redis — the serialisation round trip and the size cap, neither of which
 * {@code InMemoryPlaceSuggestionCache} can prove.
 *
 * <p>{@code cache-max-size} is turned down to 3 so eviction is exercisable; production runs it at
 * 5000.
 */
@SpringBootTest(properties = "roadscanner.google-places.cache-max-size=3")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RedisPlaceSuggestionCacheTest {

    private static final int LIMIT = 5;

    @Autowired
    private PlaceSuggestionCache cache;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void clearCache() {
        // These tests assert on cache-wide size, so they need the keyspace to themselves.
        redisTemplate.delete(redisTemplate.keys("search:places*"));
    }

    private static PlaceSuggestion suggestion(String placeId, String description) {
        return PlaceSuggestion.uncurated(new GooglePlaceId(placeId), description, "Hyderabad", "Telangana, India");
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    @Test
    void roundTripsSuggestionsThroughRedis() {
        String query = unique("hyd");
        cache.put(query, LIMIT, List.of(suggestion("place-hyd", "Hyderabad, Telangana, India")));

        List<PlaceSuggestion> cached = cache.get(query, LIMIT).orElseThrow();

        assertThat(cached).hasSize(1);
        PlaceSuggestion first = cached.getFirst();
        assertThat(first.googlePlaceId()).isEqualTo(new GooglePlaceId("place-hyd"));
        assertThat(first.description()).isEqualTo("Hyderabad, Telangana, India");
        assertThat(first.primaryText()).isEqualTo("Hyderabad");
        assertThat(first.secondaryText()).isEqualTo("Telangana, India");
    }

    @Test
    void aMissIsDistinguishableFromACachedEmptyResult() {
        String neverCached = unique("nothing");
        String cachedEmpty = unique("zzz");
        cache.put(cachedEmpty, LIMIT, List.of());

        assertThat(cache.get(neverCached, LIMIT)).isEmpty();
        // Not the same thing: Google was asked and genuinely knew nothing, which is worth
        // remembering so the next identical keystroke is not re-billed.
        assertThat(cache.get(cachedEmpty, LIMIT)).contains(List.of());
    }

    @Test
    void treatsQueriesCaseInsensitivelyAndTrimmed() {
        String query = unique("Hyd");
        cache.put(query, LIMIT, List.of(suggestion("place-hyd", "Hyderabad")));

        // The same lookup to Google — paying three times for it would be waste.
        assertThat(cache.get(query.toLowerCase(), LIMIT)).isPresent();
        assertThat(cache.get(query.toUpperCase(), LIMIT)).isPresent();
        assertThat(cache.get("  " + query + "  ", LIMIT)).isPresent();
    }

    @Test
    void keysOnTheLimitSoANarrowAnswerCannotServeAWiderRequest() {
        String query = unique("hyd");
        cache.put(query, 5, List.of(suggestion("place-hyd", "Hyderabad")));

        assertThat(cache.get(query, 5)).isPresent();
        assertThat(cache.get(query, 10)).isEmpty();
    }

    @Test
    void neverStoresCatalogueIdentitySoCurationIsAlwaysResolvedFresh() {
        String query = unique("hyd");
        PlaceSuggestion curated = suggestion("place-hyd", "Hyderabad").curatedAs(LocationId.generate());

        cache.put(query, LIMIT, List.of(curated));

        // Only the provider's own answer is cached. If a locationId were stored, removing a
        // location would leave a dangling id in the cache until the TTL expired.
        assertThat(cache.get(query, LIMIT).orElseThrow().getFirst().isCurated()).isFalse();
    }

    @Test
    void evictsTheOldestEntriesOnceTheConfiguredSizeIsExceeded() {
        cache.put("first", LIMIT, List.of(suggestion("p1", "First")));
        cache.put("second", LIMIT, List.of(suggestion("p2", "Second")));
        cache.put("third", LIMIT, List.of(suggestion("p3", "Third")));
        assertThat(cache.get("first", LIMIT)).isPresent();

        cache.put("fourth", LIMIT, List.of(suggestion("p4", "Fourth")));

        // Cap is 3: the oldest goes, the newest stays. Without this, a flood of distinct one-off
        // fragments would grow the cache without limit.
        assertThat(cache.get("first", LIMIT)).isEmpty();
        assertThat(cache.get("fourth", LIMIT)).isPresent();
    }

    @Test
    void keepsTheIndexInStepWithWhatIsActuallyCached() {
        cache.put("alpha", LIMIT, List.of(suggestion("p1", "Alpha")));
        cache.put("beta", LIMIT, List.of(suggestion("p2", "Beta")));

        Long indexed = redisTemplate.opsForZSet().zCard("search:places:index");

        // A drifting index would evict entries that no longer exist while leaving real ones in
        // place, quietly shrinking the effective cache below its configured size.
        assertThat(indexed).isEqualTo(2);
    }
}
