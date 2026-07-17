package com.roadscanner.searchservice.adapter.out.persistence;

import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.RatingSnapshot;
import com.roadscanner.searchservice.domain.model.ResultPage;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.SearchQuery;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.SortOption;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/** Exercises {@link SearchableTripRepositoryAdapter} against a real Postgres (Testcontainers) —
 * the filter/sort/pagination combinatorics that {@code SearchTripsServiceTest}'s in-memory fake
 * deliberately does not cover (see that fake's Javadoc). */
@DataJpaTest
@Import({TestcontainersConfiguration.class, SearchableTripRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SearchableTripRepositoryAdapterTest {

    private static final Route ROUTE = new Route("Mumbai", "Pune");
    private static final LocalDate TRAVEL_DATE = LocalDate.parse("2026-08-01");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-01T00:00:00Z");

    @Autowired
    private SearchableTripRepositoryAdapter adapter;

    private SearchableTrip trip(String operatorName, Instant departure, Instant arrival, BigDecimal fare,
                                 String busTypeCategory, double rating) {
        SearchableTrip trip = SearchableTrip.publish(new TripId(UUID.randomUUID()), new OperatorId(UUID.randomUUID()),
                operatorName, ROUTE, new Schedule(departure, arrival),
                new BusType(busTypeCategory, List.of("WiFi")),
                new FareSnapshot(fare, Currency.getInstance("INR")), PUBLISHED_AT);
        if (rating > 0) {
            trip.applyRatingUpdate(new RatingSnapshot(rating, 10), PUBLISHED_AT.plusSeconds(1));
        }
        return trip;
    }

    private SearchQuery baseQuery(SortOption sort) {
        return new SearchQuery(ROUTE, TRAVEL_DATE, null, null, null, null, null, null, sort, 0, 20);
    }

    @Test
    void savesAndRoundTripsATrip() {
        SearchableTrip trip = trip("Acme", Instant.parse("2026-08-01T08:00:00Z"),
                Instant.parse("2026-08-01T12:00:00Z"), BigDecimal.valueOf(500), "AC Sleeper", 4.5);

        adapter.save(trip);

        SearchableTrip found = adapter.findByTripId(trip.tripId()).orElseThrow();
        assertThat(found.operatorName()).isEqualTo("Acme");
        assertThat(found.route()).isEqualTo(ROUTE);
        assertThat(found.fare().amount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(found.busType().amenities()).containsExactly("WiFi");
        assertThat(found.rating().average()).isEqualTo(4.5);
    }

    @Test
    void findByTripIdIsEmptyForAnUnknownId() {
        assertThat(adapter.findByTripId(new TripId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void searchFiltersByRouteAndTravelDateAndExcludesUnbookableTrips() {
        SearchableTrip inWindow = trip("Acme", Instant.parse("2026-08-01T08:00:00Z"),
                Instant.parse("2026-08-01T12:00:00Z"), BigDecimal.valueOf(500), "AC Sleeper", 0);
        SearchableTrip differentDay = trip("Other", Instant.parse("2026-08-02T08:00:00Z"),
                Instant.parse("2026-08-02T12:00:00Z"), BigDecimal.valueOf(500), "AC Sleeper", 0);
        SearchableTrip cancelled = trip("Cancelled Co", Instant.parse("2026-08-01T09:00:00Z"),
                Instant.parse("2026-08-01T13:00:00Z"), BigDecimal.valueOf(500), "AC Sleeper", 0);
        cancelled.cancel(PUBLISHED_AT.plusSeconds(5));
        adapter.save(inWindow);
        adapter.save(differentDay);
        adapter.save(cancelled);

        ResultPage<SearchableTrip> result = adapter.search(baseQuery(SortOption.DEPARTURE_TIME_ASC));

        assertThat(result.content()).extracting(SearchableTrip::tripId).containsExactly(inWindow.tripId());
    }

    @Test
    void searchFiltersByFareRange() {
        SearchableTrip cheap = trip("Cheap", Instant.parse("2026-08-01T08:00:00Z"),
                Instant.parse("2026-08-01T12:00:00Z"), BigDecimal.valueOf(300), "Seater", 0);
        SearchableTrip expensive = trip("Expensive", Instant.parse("2026-08-01T09:00:00Z"),
                Instant.parse("2026-08-01T13:00:00Z"), BigDecimal.valueOf(900), "Sleeper", 0);
        adapter.save(cheap);
        adapter.save(expensive);

        SearchQuery query = new SearchQuery(ROUTE, TRAVEL_DATE, BigDecimal.valueOf(500), BigDecimal.valueOf(1000),
                null, null, null, null, SortOption.DEPARTURE_TIME_ASC, 0, 20);
        ResultPage<SearchableTrip> result = adapter.search(query);

        assertThat(result.content()).extracting(SearchableTrip::tripId).containsExactly(expensive.tripId());
    }

    @Test
    void searchFiltersByBusTypeCategoryCaseInsensitively() {
        SearchableTrip sleeper = trip("A", Instant.parse("2026-08-01T08:00:00Z"),
                Instant.parse("2026-08-01T12:00:00Z"), BigDecimal.valueOf(500), "AC Sleeper", 0);
        SearchableTrip seater = trip("B", Instant.parse("2026-08-01T09:00:00Z"),
                Instant.parse("2026-08-01T13:00:00Z"), BigDecimal.valueOf(500), "Seater", 0);
        adapter.save(sleeper);
        adapter.save(seater);

        SearchQuery query = new SearchQuery(ROUTE, TRAVEL_DATE, null, null, null, null,
                "ac sleeper", null, SortOption.DEPARTURE_TIME_ASC, 0, 20);
        ResultPage<SearchableTrip> result = adapter.search(query);

        assertThat(result.content()).extracting(SearchableTrip::tripId).containsExactly(sleeper.tripId());
    }

    @Test
    void searchFiltersByMinimumRating() {
        SearchableTrip highRated = trip("A", Instant.parse("2026-08-01T08:00:00Z"),
                Instant.parse("2026-08-01T12:00:00Z"), BigDecimal.valueOf(500), "AC Sleeper", 4.8);
        SearchableTrip lowRated = trip("B", Instant.parse("2026-08-01T09:00:00Z"),
                Instant.parse("2026-08-01T13:00:00Z"), BigDecimal.valueOf(500), "AC Sleeper", 2.0);
        adapter.save(highRated);
        adapter.save(lowRated);

        SearchQuery query = new SearchQuery(ROUTE, TRAVEL_DATE, null, null, null, null, null, 4.0,
                SortOption.DEPARTURE_TIME_ASC, 0, 20);
        ResultPage<SearchableTrip> result = adapter.search(query);

        assertThat(result.content()).extracting(SearchableTrip::tripId).containsExactly(highRated.tripId());
    }

    @Test
    void searchSortsByPriceAscending() {
        SearchableTrip cheap = trip("Cheap", Instant.parse("2026-08-01T08:00:00Z"),
                Instant.parse("2026-08-01T12:00:00Z"), BigDecimal.valueOf(300), "Seater", 0);
        SearchableTrip expensive = trip("Expensive", Instant.parse("2026-08-01T09:00:00Z"),
                Instant.parse("2026-08-01T13:00:00Z"), BigDecimal.valueOf(900), "Sleeper", 0);
        adapter.save(expensive);
        adapter.save(cheap);

        ResultPage<SearchableTrip> result = adapter.search(baseQuery(SortOption.PRICE_ASC));

        assertThat(result.content()).extracting(SearchableTrip::tripId)
                .containsExactly(cheap.tripId(), expensive.tripId());
    }

    @Test
    void searchSortsByDurationAscendingUsingTheGeneratedColumn() {
        // A later departure but a much shorter trip must sort first — proves duration_seconds
        // is a real generated column, not a departure/arrival timestamp proxy.
        SearchableTrip longTrip = trip("Long", Instant.parse("2026-08-01T06:00:00Z"),
                Instant.parse("2026-08-01T18:00:00Z"), BigDecimal.valueOf(500), "Seater", 0);
        SearchableTrip shortTrip = trip("Short", Instant.parse("2026-08-01T10:00:00Z"),
                Instant.parse("2026-08-01T11:00:00Z"), BigDecimal.valueOf(500), "Seater", 0);
        adapter.save(longTrip);
        adapter.save(shortTrip);

        ResultPage<SearchableTrip> result = adapter.search(baseQuery(SortOption.DURATION_ASC));

        assertThat(result.content()).extracting(SearchableTrip::tripId)
                .containsExactly(shortTrip.tripId(), longTrip.tripId());
    }

    @Test
    void searchPaginatesResults() {
        for (int i = 0; i < 5; i++) {
            adapter.save(trip("Operator" + i, Instant.parse("2026-08-01T0" + i + ":00:00Z"),
                    Instant.parse("2026-08-01T1" + i + ":00:00Z"), BigDecimal.valueOf(100 * (i + 1)), "Seater", 0));
        }

        SearchQuery firstPage = new SearchQuery(ROUTE, TRAVEL_DATE, null, null, null, null, null, null,
                SortOption.PRICE_ASC, 0, 2);
        ResultPage<SearchableTrip> page = adapter.search(firstPage);

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void suggestPlacesMatchesOriginsAndDestinationsByPrefixCaseInsensitively() {
        adapter.save(trip("A", Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"),
                BigDecimal.valueOf(500), "Seater", 0));

        assertThat(adapter.suggestPlaces("mum", 10)).contains("Mumbai");
        assertThat(adapter.suggestPlaces("Pu", 10)).contains("Pune");
        assertThat(adapter.suggestPlaces("zzz", 10)).isEmpty();
    }

    @Test
    void suggestPlacesExcludesUnbookableTrips() {
        SearchableTrip cancelled = trip("Cancelled", Instant.parse("2026-08-01T08:00:00Z"),
                Instant.parse("2026-08-01T12:00:00Z"), BigDecimal.valueOf(500), "Seater", 0);
        cancelled.cancel(PUBLISHED_AT.plusSeconds(5));
        adapter.save(cancelled);

        assertThat(adapter.suggestPlaces("Mum", 10)).isEmpty();
    }

    @Test
    void deleteAllDiscardsEveryRow() {
        adapter.save(trip("A", Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"),
                BigDecimal.valueOf(500), "Seater", 0));

        adapter.deleteAll();

        assertThat(adapter.search(baseQuery(SortOption.DEPARTURE_TIME_ASC)).totalElements()).isZero();
    }
}
