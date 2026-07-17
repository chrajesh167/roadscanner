package com.roadscanner.searchservice.application.usecase.indexing;

import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.in.IndexTripPublished;
import com.roadscanner.searchservice.testsupport.fakes.InMemorySearchableTripRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripPublishedIndexerTest {

    private static final TripId TRIP_ID = new TripId(UUID.randomUUID());
    private static final OperatorId OPERATOR_ID = new OperatorId(UUID.randomUUID());
    private static final Route ROUTE = new Route("Mumbai", "Pune");
    private static final Schedule SCHEDULE = new Schedule(
            Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"));
    private static final BusType BUS_TYPE = new BusType("AC Sleeper", List.of());
    private static final FareSnapshot FARE = new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR"));
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-01T00:00:00Z");

    private final InMemorySearchableTripRepository repository = new InMemorySearchableTripRepository();
    private final TripPublishedIndexer indexer = new TripPublishedIndexer(repository);

    private IndexTripPublished.IndexTripPublishedCommand command(Instant occurredAt) {
        return new IndexTripPublished.IndexTripPublishedCommand(
                TRIP_ID, OPERATOR_ID, "Acme", ROUTE, SCHEDULE, BUS_TYPE, FARE, occurredAt);
    }

    @Test
    void createsANewProjectionForAPreviouslyUnseenTrip() {
        indexer.index(command(OCCURRED_AT));

        SearchableTrip indexed = repository.findByTripId(TRIP_ID).orElseThrow();
        assertThat(indexed.operatorName()).isEqualTo("Acme");
        assertThat(indexed.bookable()).isTrue();
    }

    @Test
    void redeliveryForAnAlreadyIndexedTripIsAppliedAsAnUpdate() {
        indexer.index(command(OCCURRED_AT));

        indexer.index(new IndexTripPublished.IndexTripPublishedCommand(
                TRIP_ID, OPERATOR_ID, "Acme Renamed", ROUTE, SCHEDULE, BUS_TYPE, FARE, OCCURRED_AT.plusSeconds(60)));

        assertThat(repository.findByTripId(TRIP_ID).orElseThrow().operatorName()).isEqualTo("Acme Renamed");
    }

    @Test
    void staleRedeliveryIsIgnored() {
        indexer.index(command(OCCURRED_AT));
        indexer.index(new IndexTripPublished.IndexTripPublishedCommand(
                TRIP_ID, OPERATOR_ID, "Second", ROUTE, SCHEDULE, BUS_TYPE, FARE, OCCURRED_AT.plusSeconds(60)));

        indexer.index(new IndexTripPublished.IndexTripPublishedCommand(
                TRIP_ID, OPERATOR_ID, "Stale", ROUTE, SCHEDULE, BUS_TYPE, FARE, OCCURRED_AT.plusSeconds(30)));

        assertThat(repository.findByTripId(TRIP_ID).orElseThrow().operatorName()).isEqualTo("Second");
    }

    @Test
    void redeliveryAfterCancellationDoesNotResurrectTheTrip() {
        indexer.index(command(OCCURRED_AT));
        repository.findByTripId(TRIP_ID).orElseThrow().cancel(OCCURRED_AT.plusSeconds(10));

        indexer.index(new IndexTripPublished.IndexTripPublishedCommand(
                TRIP_ID, OPERATOR_ID, "Acme", ROUTE, SCHEDULE, BUS_TYPE, FARE, OCCURRED_AT.plusSeconds(20)));

        assertThat(repository.findByTripId(TRIP_ID).orElseThrow().bookable()).isFalse();
    }
}
