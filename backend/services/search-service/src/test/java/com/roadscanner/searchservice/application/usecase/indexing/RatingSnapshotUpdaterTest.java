package com.roadscanner.searchservice.application.usecase.indexing;

import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.RatingSnapshot;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.in.UpdateRatingSnapshot;
import com.roadscanner.searchservice.testsupport.fakes.InMemorySearchableTripRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RatingSnapshotUpdaterTest {

    private static final TripId TRIP_ID = new TripId(UUID.randomUUID());
    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-01T00:00:00Z");

    private final InMemorySearchableTripRepository repository = new InMemorySearchableTripRepository();
    private final RatingSnapshotUpdater updater = new RatingSnapshotUpdater(repository);

    @Test
    void appliesTheRatingToAnIndexedTrip() {
        repository.save(SearchableTrip.publish(TRIP_ID, new OperatorId(UUID.randomUUID()), "Acme",
                new Route("Mumbai", "Pune"),
                new Schedule(Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z")),
                new BusType("AC Sleeper", List.of()),
                new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR")), PUBLISHED_AT));

        updater.update(new UpdateRatingSnapshot.UpdateRatingSnapshotCommand(
                TRIP_ID, new RatingSnapshot(4.2, 8), PUBLISHED_AT.plusSeconds(10)));

        assertThat(repository.findByTripId(TRIP_ID).orElseThrow().rating()).isEqualTo(new RatingSnapshot(4.2, 8));
    }

    @Test
    void missingProjectionIsASilentNoOp() {
        assertThatCode(() -> updater.update(new UpdateRatingSnapshot.UpdateRatingSnapshotCommand(
                TRIP_ID, new RatingSnapshot(4.2, 8), PUBLISHED_AT)))
                .doesNotThrowAnyException();
        assertThat(repository.findByTripId(TRIP_ID)).isEmpty();
    }
}
