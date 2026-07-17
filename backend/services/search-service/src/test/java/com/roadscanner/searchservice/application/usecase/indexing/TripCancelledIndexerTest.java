package com.roadscanner.searchservice.application.usecase.indexing;

import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.in.IndexTripCancelled;
import com.roadscanner.searchservice.testsupport.fakes.InMemorySearchableTripRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TripCancelledIndexerTest {

    private static final TripId TRIP_ID = new TripId(UUID.randomUUID());
    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-01T00:00:00Z");

    private final InMemorySearchableTripRepository repository = new InMemorySearchableTripRepository();
    private final TripCancelledIndexer indexer = new TripCancelledIndexer(repository);

    private void publishTrip() {
        repository.save(SearchableTrip.publish(TRIP_ID, new OperatorId(UUID.randomUUID()), "Acme",
                new Route("Mumbai", "Pune"),
                new Schedule(Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z")),
                new BusType("AC Sleeper", List.of()),
                new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR")), PUBLISHED_AT));
    }

    @Test
    void marksAnIndexedTripUnbookable() {
        publishTrip();

        indexer.index(new IndexTripCancelled.IndexTripCancelledCommand(TRIP_ID, PUBLISHED_AT.plusSeconds(10)));

        assertThat(repository.findByTripId(TRIP_ID).orElseThrow().bookable()).isFalse();
    }

    @Test
    void isIdempotent() {
        publishTrip();
        indexer.index(new IndexTripCancelled.IndexTripCancelledCommand(TRIP_ID, PUBLISHED_AT.plusSeconds(10)));

        assertThatCode(() -> indexer.index(
                new IndexTripCancelled.IndexTripCancelledCommand(TRIP_ID, PUBLISHED_AT.plusSeconds(20))))
                .doesNotThrowAnyException();
        assertThat(repository.findByTripId(TRIP_ID).orElseThrow().bookable()).isFalse();
    }

    @Test
    void missingProjectionIsASilentNoOp() {
        assertThatCode(() -> indexer.index(
                new IndexTripCancelled.IndexTripCancelledCommand(TRIP_ID, PUBLISHED_AT)))
                .doesNotThrowAnyException();
        assertThat(repository.findByTripId(TRIP_ID)).isEmpty();
    }
}
