package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.exception.PlaceAutocompleteUnavailableException;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.PlaceSuggestion;
import com.roadscanner.searchservice.location.domain.port.in.SearchPlaceSuggestions;
import com.roadscanner.searchservice.location.testsupport.InMemoryLocationRepository;
import com.roadscanner.searchservice.location.testsupport.InMemoryPlaceSuggestionCache;
import com.roadscanner.searchservice.location.testsupport.StubGooglePlacesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.roadscanner.searchservice.location.testsupport.StubGooglePlacesClient.suggestion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The three rules this use case owns: clamp the limit, cache successes only, and enrich without
 * ever writing to the catalogue.
 */
class SearchPlaceSuggestionsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final LocationAddress HYDERABAD = new LocationAddress("Hyderabad", "Telangana", "India");

    private StubGooglePlacesClient places;
    private InMemoryPlaceSuggestionCache cache;
    private InMemoryLocationRepository locations;
    private SearchPlaceSuggestions useCase;

    @BeforeEach
    void setUp() {
        places = new StubGooglePlacesClient();
        cache = new InMemoryPlaceSuggestionCache();
        locations = new InMemoryLocationRepository();
        useCase = new SearchPlaceSuggestionsService(places, cache, locations);
    }

    private SearchPlaceSuggestions.SearchPlaceSuggestionsResult search(String query, int limit) {
        return useCase.search(new SearchPlaceSuggestions.SearchPlaceSuggestionsCommand(query, limit));
    }

    @Nested
    class Lookup {

        @Test
        void returnsProviderSuggestions() {
            places.returning(suggestion("place-hyd", "Hyderabad, Telangana, India"));

            var result = search("hyd", 5);

            assertThat(result.suggestions()).extracting(PlaceSuggestion::description)
                    .containsExactly("Hyderabad, Telangana, India");
            assertThat(result.cached()).isFalse();
        }

        @Test
        void returnsAnEmptyListWhenTheProviderKnowsNothing() {
            places.returning();

            assertThat(search("zzz", 5).suggestions()).isEmpty();
        }

        @Test
        void rejectsABlankQueryWithoutEverCallingThePaidProvider() {
            assertThatThrownBy(() -> search("   ", 5)).isInstanceOf(IllegalArgumentException.class);

            assertThat(places.calls()).isEmpty();
        }

        @Test
        void rejectsANonPositiveLimit() {
            assertThatThrownBy(() -> search("hyd", 0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void clampsAnOversizedLimitSoAKeystrokeCannotOrderAnUnboundedPage() {
            places.returning();

            search("hyd", 5_000);

            assertThat(places.calls()).containsExactly("hyd|" + SearchPlaceSuggestionsService.MAX_LIMIT);
        }
    }

    @Nested
    class Caching {

        @Test
        void cachesASuccessfulLookup() {
            places.returning(suggestion("place-hyd", "Hyderabad, Telangana, India"));

            search("hyd", 5);

            assertThat(cache.size()).isEqualTo(1);
        }

        @Test
        void servesARepeatedQueryFromCacheWithoutCallingTheProviderAgain() {
            places.returning(suggestion("place-hyd", "Hyderabad, Telangana, India"));
            search("hyd", 5);

            var second = search("hyd", 5);

            assertThat(second.cached()).isTrue();
            assertThat(second.suggestions()).hasSize(1);
            // One paid call, two answers — the entire point of the cache.
            assertThat(places.calls()).hasSize(1);
        }

        @Test
        void cachesAGenuinelyEmptyResultSoARepeatedMissIsNotRebilled() {
            places.returning();
            search("zzz", 5);

            var second = search("zzz", 5);

            assertThat(second.cached()).isTrue();
            assertThat(second.suggestions()).isEmpty();
            assertThat(places.calls()).hasSize(1);
        }

        @Test
        void neverCachesAProviderFailure() {
            places.failing();

            assertThatThrownBy(() -> search("hyd", 5))
                    .isInstanceOf(PlaceAutocompleteUnavailableException.class);

            // The critical rule: a thirty-second outage must not become ten minutes of empty
            // dropdowns because the failure got frozen into the cache.
            assertThat(cache.writes()).isEmpty();
            assertThat(cache.size()).isZero();
        }

        @Test
        void recoversImmediatelyOnceTheProviderIsHealthyAgain() {
            places.failing();
            assertThatThrownBy(() -> search("hyd", 5)).isInstanceOf(PlaceAutocompleteUnavailableException.class);

            places.returning(suggestion("place-hyd", "Hyderabad, Telangana, India"));
            var afterRecovery = search("hyd", 5);

            assertThat(afterRecovery.suggestions()).hasSize(1);
            assertThat(afterRecovery.cached()).isFalse();
        }

        @Test
        void treatsDifferentLimitsAsDifferentCacheEntries() {
            places.returning(suggestion("place-hyd", "Hyderabad, Telangana, India"));
            search("hyd", 5);

            var wider = search("hyd", 10);

            // A 5-result answer cannot serve a 10-result request.
            assertThat(wider.cached()).isFalse();
            assertThat(places.calls()).hasSize(2);
        }
    }

    @Nested
    class Enrichment {

        @Test
        void annotatesASuggestionAlreadyInTheCatalogue() {
            Location curated = Location.create(LocationId.generate(), "Hyderabad", HYDERABAD, null,
                    new GooglePlaceId("place-hyd"), null, NOW);
            locations.seed(curated);
            places.returning(suggestion("place-hyd", "Hyderabad, Telangana, India"));

            PlaceSuggestion suggestion = search("hyd", 5).suggestions().getFirst();

            assertThat(suggestion.isCurated()).isTrue();
            assertThat(suggestion.locationIdIfPresent()).contains(curated.id());
        }

        @Test
        void leavesAnUncuratedSuggestionUnannotated() {
            places.returning(suggestion("place-unknown", "Nowhere, India"));

            PlaceSuggestion suggestion = search("nowhere", 5).suggestions().getFirst();

            assertThat(suggestion.isCurated()).isFalse();
            assertThat(suggestion.locationIdIfPresent()).isEmpty();
        }

        @Test
        void neverWritesToTheCatalogue() {
            places.returning(suggestion("place-hyd", "Hyderabad, Telangana, India"));

            search("hyd", 5);

            // Google enriches; it does not author. Nothing it returned may become a catalogue row.
            assertThat(locations.count()).isZero();
        }

        @Test
        void reflectsACurationThatHappenedAfterTheAnswerWasCached() {
            places.returning(suggestion("place-hyd", "Hyderabad, Telangana, India"));
            search("hyd", 5);
            assertThat(cache.size()).isEqualTo(1);

            Location curated = Location.create(LocationId.generate(), "Hyderabad", HYDERABAD, null,
                    new GooglePlaceId("place-hyd"), null, NOW);
            locations.seed(curated);

            // The catalogue changes independently of Google, so enrichment is resolved on every
            // read — a location curated a moment ago must not wait out the TTL to show as curated.
            var fromCache = search("hyd", 5);

            assertThat(fromCache.cached()).isTrue();
            assertThat(fromCache.suggestions().getFirst().locationIdIfPresent()).contains(curated.id());
        }
    }
}
