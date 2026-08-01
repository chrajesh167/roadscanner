package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.ProviderTripResult;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.port.out.ProviderTripSearchClient;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.model.ProviderPlaceRef;
import com.roadscanner.searchservice.location.domain.port.in.SearchProviderTrips;
import com.roadscanner.searchservice.location.testsupport.InMemoryProviderLocationMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Federation: discover which providers can serve the route, ask them all, aggregate, and keep one
 * provider's failure from touching the others.
 */
class SearchProviderTripsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private static final ProviderCode FLIXBUS = new ProviderCode("FLIXBUS");
    private static final ProviderCode REDBUS = new ProviderCode("REDBUS");
    private static final ProviderCode ABHIBUS = new ProviderCode("ABHIBUS");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 1);

    /** Per-provider scripted behaviour, so one provider can be made to fail in isolation. */
    private static final class ScriptedSearchClient implements ProviderTripSearchClient {
        private final Map<ProviderCode, Supplier<List<ProviderTripResult>>> behaviour = new HashMap<>();
        private final List<String> calls = new ArrayList<>();

        @Override
        public List<ProviderTripResult> search(ProviderCode provider, String originCityId,
                                               String destinationCityId, LocalDate travelDate) {
            calls.add(provider + "|" + originCityId + "|" + destinationCityId);
            return behaviour.getOrDefault(provider, List::of).get();
        }

        void returns(ProviderCode provider, ProviderTripResult... trips) {
            behaviour.put(provider, () -> List.of(trips));
        }

        void fails(ProviderCode provider, RuntimeException failure) {
            behaviour.put(provider, () -> {
                throw failure;
            });
        }
    }

    private InMemoryProviderLocationMappingRepository mappings;
    private ScriptedSearchClient client;
    private SearchProviderTrips useCase;
    private LocationId hyderabad;
    private LocationId pune;

    @BeforeEach
    void setUp() {
        mappings = new InMemoryProviderLocationMappingRepository();
        client = new ScriptedSearchClient();
        useCase = new SearchProviderTripsService(mappings, client);
        hyderabad = LocationId.generate();
        pune = LocationId.generate();
    }

    private void map(ProviderCode provider, LocationId locationId, String cityId, String stationId) {
        mappings.seed(ProviderLocationMapping.create(ProviderLocationMappingId.generate(), provider, locationId,
                new ProviderPlaceRef(cityId, stationId, null), null, NOW));
    }

    private void mapRoute(ProviderCode provider, String originCityId, String destinationCityId) {
        map(provider, hyderabad, originCityId, null);
        map(provider, pune, destinationCityId, null);
    }

    private static ProviderTripResult trip(ProviderCode provider, String id, int departureHour) {
        return new ProviderTripResult(provider.value(), id, "Carrier",
                new Route("Hyderabad", "Pune"),
                new Schedule(NOW.plusSeconds(departureHour * 3600L), NOW.plusSeconds((departureHour + 6) * 3600L)),
                "AC Sleeper", new FareSnapshot(BigDecimal.valueOf(900), Currency.getInstance("INR")), 12,
                "point-a", "point-b");
    }

    private SearchProviderTrips.Result search() {
        return useCase.search(new SearchProviderTrips.Command(hyderabad, pune, DATE));
    }

    @Nested
    class Discovery {

        @Test
        void asksEveryProviderThatCanExpressTheRoute() {
            mapRoute(FLIXBUS, "58291", "41100");
            mapRoute(REDBUS, "RB-HYD", "RB-PNQ");

            search();

            // Each provider asked in its own vocabulary — no id crossed providers.
            assertThat(client.calls).containsExactlyInAnyOrder(
                    "FLIXBUS|58291|41100", "REDBUS|RB-HYD|RB-PNQ");
        }

        @Test
        void skipsAProviderMappedAtOnlyOneEnd() {
            mapRoute(FLIXBUS, "58291", "41100");
            map(REDBUS, hyderabad, "RB-HYD", null);

            var result = search();

            // A provider that cannot name one end cannot express the route.
            assertThat(client.calls).containsExactly("FLIXBUS|58291|41100");
            assertThat(result.queried()).containsExactly(FLIXBUS);
        }

        @Test
        void skipsAStationOnlyMappingBecauseSearchNeedsACityId() {
            mapRoute(FLIXBUS, "58291", "41100");
            map(REDBUS, hyderabad, null, "RB-STATION");
            map(REDBUS, pune, null, "RB-STATION-2");

            assertThat(search().queried()).containsExactly(FLIXBUS);
        }

        @Test
        void performsNoSearchWhenNoProviderCanServeTheRoute() {
            var result = search();

            assertThat(client.calls).isEmpty();
            assertThat(result.trips()).isEmpty();
            assertThat(result.queried()).isEmpty();
            // Nothing failed — nothing was attempted.
            assertThat(result.complete()).isTrue();
        }

        @Test
        void discoveryIsDataDrivenSoOnboardingNeedsNoCodeChange() {
            mapRoute(FLIXBUS, "58291", "41100");
            assertThat(search().queried()).containsExactly(FLIXBUS);

            // Inserting mappings alone makes a new provider searchable.
            mapRoute(ABHIBUS, "AB-HYD", "AB-PNQ");

            assertThat(search().queried()).containsExactlyInAnyOrder(FLIXBUS, ABHIBUS);
        }
    }

    @Nested
    class Aggregation {

        @Test
        void mergesResultsFromEveryProvider() {
            mapRoute(FLIXBUS, "58291", "41100");
            mapRoute(REDBUS, "RB-HYD", "RB-PNQ");
            client.returns(FLIXBUS, trip(FLIXBUS, "fb-1", 8));
            client.returns(REDBUS, trip(REDBUS, "rb-1", 9));

            var result = search();

            assertThat(result.trips()).extracting(ProviderTripResult::providerTripId)
                    .containsExactly("fb-1", "rb-1");
            assertThat(result.succeeded()).containsExactlyInAnyOrder(FLIXBUS, REDBUS);
        }

        @Test
        void ordersByDepartureTimeRatherThanByProvider() {
            mapRoute(FLIXBUS, "58291", "41100");
            mapRoute(REDBUS, "RB-HYD", "RB-PNQ");
            client.returns(FLIXBUS, trip(FLIXBUS, "fb-late", 18));
            client.returns(REDBUS, trip(REDBUS, "rb-early", 6));

            // Concatenating in call order would rank providers by how the fan-out happened to
            // iterate, quietly favouring whichever was mapped first.
            assertThat(search().trips()).extracting(ProviderTripResult::providerTripId)
                    .containsExactly("rb-early", "fb-late");
        }

        @Test
        void aProviderWithNoTripsStillCountsAsSucceeded() {
            mapRoute(FLIXBUS, "58291", "41100");
            mapRoute(REDBUS, "RB-HYD", "RB-PNQ");
            client.returns(FLIXBUS, trip(FLIXBUS, "fb-1", 8));

            var result = search();

            // "Answered with nothing" is not a failure.
            assertThat(result.succeeded()).containsExactlyInAnyOrder(FLIXBUS, REDBUS);
            assertThat(result.complete()).isTrue();
        }
    }

    @Nested
    class Isolation {

        private void mapThreeProviders() {
            mapRoute(FLIXBUS, "58291", "41100");
            mapRoute(REDBUS, "RB-HYD", "RB-PNQ");
            mapRoute(ABHIBUS, "AB-HYD", "AB-PNQ");
        }

        @Test
        void oneProviderFailingStillReturnsTheOthersResults() {
            mapThreeProviders();
            client.returns(FLIXBUS, trip(FLIXBUS, "fb-1", 8));
            client.fails(REDBUS, new IllegalStateException("provider exploded"));
            client.returns(ABHIBUS, trip(ABHIBUS, "ab-1", 10));

            var result = search();

            // The whole point: a bad day at one provider must not empty a traveller's search.
            assertThat(result.trips()).extracting(ProviderTripResult::providerTripId)
                    .containsExactly("fb-1", "ab-1");
            assertThat(result.succeeded()).containsExactlyInAnyOrder(FLIXBUS, ABHIBUS);
            assertThat(result.failed()).containsExactly(REDBUS);
        }

        @Test
        void reportsThatTheAnswerIsIncompleteWhenAProviderFailed() {
            mapThreeProviders();
            client.fails(REDBUS, new RuntimeException("timeout"));

            var result = search();

            // A partial result presented as the whole market is worse than a visibly partial one.
            assertThat(result.complete()).isFalse();
            assertThat(result.queried()).hasSize(3);
        }

        @Test
        void containsAnyFailureKindNotJustAnticipatedOnes() {
            // Isolation must hold for failures nobody thought of — those are the ones that take a
            // system down — so the guard is deliberately not a curated exception list.
            for (RuntimeException failure : List.of(
                    new IllegalStateException("unexpected"),
                    new IllegalArgumentException("validation"),
                    new NullPointerException("bug"),
                    new UnsupportedOperationException("rate limited"))) {
                setUp();
                mapRoute(FLIXBUS, "58291", "41100");
                mapRoute(REDBUS, "RB-HYD", "RB-PNQ");
                client.returns(FLIXBUS, trip(FLIXBUS, "fb-1", 8));
                client.fails(REDBUS, failure);

                var result = search();

                assertThat(result.trips()).hasSize(1);
                assertThat(result.failed()).containsExactly(REDBUS);
            }
        }

        @Test
        void everyProviderFailingYieldsAnEmptyButHonestAnswer() {
            mapThreeProviders();
            client.fails(FLIXBUS, new RuntimeException("down"));
            client.fails(REDBUS, new RuntimeException("down"));
            client.fails(ABHIBUS, new RuntimeException("down"));

            var result = search();

            // Empty, but reported as incomplete rather than as "no trips exist".
            assertThat(result.trips()).isEmpty();
            assertThat(result.complete()).isFalse();
            assertThat(result.failed()).hasSize(3);
        }
    }

    @Test
    void rejectsAnIdenticalOriginAndDestination() {
        assertThatThrownBy(() -> new SearchProviderTrips.Command(hyderabad, hyderabad, DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingArguments() {
        assertThatThrownBy(() -> new SearchProviderTrips.Command(null, pune, DATE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SearchProviderTrips.Command(hyderabad, pune, null))
                .isInstanceOf(NullPointerException.class);
    }
}
