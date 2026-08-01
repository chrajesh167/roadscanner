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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Location translation: canonical {@code LocationId} in, provider city ids out, and the rule that
 * an unmapped route is answered rather than guessed.
 */
class SearchProviderTripsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final ProviderCode FLIXBUS = new ProviderCode("FLIXBUS");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 1);

    /** Records exactly what the client was asked for, so translation can be asserted. */
    private static final class RecordingSearchClient implements ProviderTripSearchClient {
        private final List<String> calls = new ArrayList<>();
        private List<ProviderTripResult> response = List.of();

        @Override
        public List<ProviderTripResult> search(ProviderCode provider, String originCityId,
                                               String destinationCityId, LocalDate travelDate) {
            calls.add(provider + "|" + originCityId + "|" + destinationCityId + "|" + travelDate);
            return response;
        }
    }

    private InMemoryProviderLocationMappingRepository mappings;
    private RecordingSearchClient client;
    private SearchProviderTrips useCase;
    private LocationId hyderabad;
    private LocationId pune;

    @BeforeEach
    void setUp() {
        mappings = new InMemoryProviderLocationMappingRepository();
        client = new RecordingSearchClient();
        useCase = new SearchProviderTripsService(mappings, client);
        hyderabad = LocationId.generate();
        pune = LocationId.generate();
    }

    private void map(LocationId locationId, String cityId, String stationId) {
        mappings.seed(ProviderLocationMapping.create(ProviderLocationMappingId.generate(), FLIXBUS, locationId,
                new ProviderPlaceRef(cityId, stationId, null), null, NOW));
    }

    private static ProviderTripResult result() {
        return new ProviderTripResult("FLIXBUS", "ride-1", "FlixBus",
                new Route("Hyderabad", "Pune"),
                new Schedule(NOW.plusSeconds(3600), NOW.plusSeconds(7200)),
                "AC Sleeper", new FareSnapshot(BigDecimal.valueOf(900), Currency.getInstance("INR")), 12,
                "station-a", "station-b");
    }

    private SearchProviderTrips.Result search() {
        return useCase.search(new SearchProviderTrips.Command(FLIXBUS, hyderabad, pune, DATE));
    }

    @Test
    void translatesBothLocationsIntoTheProvidersOwnCityIds() {
        map(hyderabad, "58291", null);
        map(pune, "41100", null);
        client.response = List.of(result());

        var result = search();

        // The caller passed canonical LocationIds; the provider was asked in its own vocabulary.
        assertThat(client.calls).containsExactly("FLIXBUS|58291|41100|2026-08-01");
        assertThat(result.mapped()).isTrue();
        assertThat(result.trips()).hasSize(1);
    }

    @Test
    void reportsUnmappedWhenTheOriginHasNoMapping() {
        map(pune, "41100", null);

        var result = search();

        // Never guessed, never defaulted — asking the provider about a place we cannot identify
        // would return trips for the wrong route and present them as the requested one.
        assertThat(result.mapped()).isFalse();
        assertThat(result.trips()).isEmpty();
        assertThat(client.calls).isEmpty();
    }

    @Test
    void reportsUnmappedWhenTheDestinationHasNoMapping() {
        map(hyderabad, "58291", null);

        assertThat(search().mapped()).isFalse();
        assertThat(client.calls).isEmpty();
    }

    @Test
    void treatsAStationOnlyMappingAsUnmappedForSearch() {
        map(hyderabad, null, "station-only");
        map(pune, "41100", null);

        // Search needs a city id specifically. Substituting a station id would send the provider
        // something that is not the thing it asked for.
        assertThat(search().mapped()).isFalse();
        assertThat(client.calls).isEmpty();
    }

    @Test
    void distinguishesAnUnmappedRouteFromAMappedRouteWithNoTrips() {
        map(hyderabad, "58291", null);
        map(pune, "41100", null);
        client.response = List.of();

        var result = search();

        // "We never asked" and "we asked and there was nothing" are different answers.
        assertThat(result.mapped()).isTrue();
        assertThat(result.trips()).isEmpty();
        assertThat(client.calls).hasSize(1);
    }

    @Test
    void onlyConsultsMappingsForTheRequestedProvider() {
        map(hyderabad, "58291", null);
        map(pune, "41100", null);
        mappings.seed(ProviderLocationMapping.create(ProviderLocationMappingId.generate(),
                new ProviderCode("REDBUS"), hyderabad, new ProviderPlaceRef("RB-HYD", null, null), null, NOW));
        client.response = List.of(result());

        search();

        // One provider's id must never be sent to another.
        assertThat(client.calls).containsExactly("FLIXBUS|58291|41100|2026-08-01");
    }

    @Test
    void rejectsAnIdenticalOriginAndDestination() {
        assertThatThrownBy(() -> new SearchProviderTrips.Command(FLIXBUS, hyderabad, hyderabad, DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingArguments() {
        assertThatThrownBy(() -> new SearchProviderTrips.Command(null, hyderabad, pune, DATE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SearchProviderTrips.Command(FLIXBUS, hyderabad, pune, null))
                .isInstanceOf(NullPointerException.class);
    }
}
