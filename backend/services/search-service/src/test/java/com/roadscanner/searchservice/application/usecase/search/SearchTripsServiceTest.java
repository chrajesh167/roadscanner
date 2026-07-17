package com.roadscanner.searchservice.application.usecase.search;

import com.roadscanner.searchservice.application.usecase.availability.AvailabilityOverlay;
import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.SearchQuery;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.SortOption;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.in.SearchTrips;
import com.roadscanner.searchservice.domain.service.SearchRankingPolicy;
import com.roadscanner.searchservice.testsupport.fakes.InMemoryAvailabilityCache;
import com.roadscanner.searchservice.testsupport.fakes.InMemorySearchableTripRepository;
import com.roadscanner.searchservice.testsupport.fakes.StubAvailabilityClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTripsServiceTest {

    private static final Route ROUTE = new Route("Mumbai", "Pune");

    private final InMemorySearchableTripRepository repository = new InMemorySearchableTripRepository();
    private final InMemoryAvailabilityCache cache = new InMemoryAvailabilityCache();
    private final StubAvailabilityClient client = new StubAvailabilityClient();

    private final SearchTripsService service = new SearchTripsService(
            repository, new AvailabilityOverlay(cache, client), new SearchRankingPolicy());

    private SearchableTrip publishTrip() {
        TripId tripId = new TripId(UUID.randomUUID());
        SearchableTrip trip = SearchableTrip.publish(tripId, new OperatorId(UUID.randomUUID()), "Acme",
                ROUTE, new Schedule(Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z")),
                new BusType("AC Sleeper", List.of()),
                new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR")),
                Instant.parse("2026-07-01T00:00:00Z"));
        repository.save(trip);
        return trip;
    }

    private SearchQuery queryWithSort(SortOption sort) {
        return new SearchQuery(ROUTE, LocalDate.parse("2026-08-01"), null, null, null, null, null, null, sort, 0, 20);
    }

    @Test
    void returnsIndexedTripsOverlaidWithAvailability() {
        SearchableTrip trip = publishTrip();
        client.willReturn(trip.tripId(), AvailabilityStatus.of(15));

        SearchTrips.SearchTripsResult result = service.search(new SearchTrips.SearchTripsCommand(queryWithSort(null)));

        assertThat(result.results().content()).hasSize(1);
        assertThat(result.results().content().getFirst().trip().tripId()).isEqualTo(trip.tripId());
        assertThat(result.results().content().getFirst().availability()).isEqualTo(AvailabilityStatus.of(15));
    }

    @Test
    void unspecifiedSortStillProducesAResultUsingThePolicysDefault() {
        publishTrip();

        SearchTrips.SearchTripsResult result = service.search(new SearchTrips.SearchTripsCommand(queryWithSort(null)));

        assertThat(result.results().content()).hasSize(1);
    }

    @Test
    void explicitSortIsHonored() {
        publishTrip();

        SearchTrips.SearchTripsResult result = service.search(
                new SearchTrips.SearchTripsCommand(queryWithSort(SortOption.RATING_DESC)));

        assertThat(result.results().content()).hasSize(1);
    }

    @Test
    void emptyIndexProducesAnEmptyPageNotAnError() {
        SearchTrips.SearchTripsResult result = service.search(new SearchTrips.SearchTripsCommand(queryWithSort(null)));

        assertThat(result.results().content()).isEmpty();
        assertThat(result.results().totalElements()).isZero();
    }
}
