package com.roadscanner.searchservice.application.usecase.indexing;

import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.in.IndexTripUpdated;
import com.roadscanner.searchservice.testsupport.fakes.InMemorySearchableTripRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TripUpdatedIndexerTest {

    private static final TripId TRIP_ID = new TripId(UUID.randomUUID());
    private static final Route ROUTE = new Route("Mumbai", "Pune");
    private static final Schedule SCHEDULE = new Schedule(
            Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"));
    private static final BusType BUS_TYPE = new BusType("AC Sleeper", List.of());
    private static final FareSnapshot FARE = new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR"));
    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-01T00:00:00Z");

    private final InMemorySearchableTripRepository repository = new InMemorySearchableTripRepository();
    private final TripUpdatedIndexer indexer = new TripUpdatedIndexer(repository);

    @Test
    void appliesTheUpdateToAnExistingProjection() {
        repository.save(SearchableTrip.publish(TRIP_ID, new OperatorId(UUID.randomUUID()), "Acme",
                ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT));

        indexer.index(new IndexTripUpdated.IndexTripUpdatedCommand(
                TRIP_ID, "Acme Renamed", ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT.plusSeconds(60)));

        assertThat(repository.findByTripId(TRIP_ID).orElseThrow().operatorName()).isEqualTo("Acme Renamed");
    }

    @Test
    void missingProjectionIsLoggedAndDiscardedRatherThanThrowing() {
        assertThatCode(() -> indexer.index(new IndexTripUpdated.IndexTripUpdatedCommand(
                TRIP_ID, "Acme", ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT)))
                .doesNotThrowAnyException();

        assertThat(repository.findByTripId(TRIP_ID)).isEmpty();
    }

    @Test
    void staleUpdateIsIgnored() {
        repository.save(SearchableTrip.publish(TRIP_ID, new OperatorId(UUID.randomUUID()), "Acme",
                ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT));
        indexer.index(new IndexTripUpdated.IndexTripUpdatedCommand(
                TRIP_ID, "Second", ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT.plusSeconds(60)));

        indexer.index(new IndexTripUpdated.IndexTripUpdatedCommand(
                TRIP_ID, "Stale", ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT.plusSeconds(30)));

        assertThat(repository.findByTripId(TRIP_ID).orElseThrow().operatorName()).isEqualTo("Second");
    }
}
