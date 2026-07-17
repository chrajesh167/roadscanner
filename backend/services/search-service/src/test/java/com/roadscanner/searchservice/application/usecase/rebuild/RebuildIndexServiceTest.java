package com.roadscanner.searchservice.application.usecase.rebuild;

import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.testsupport.fakes.InMemorySearchableTripRepository;
import com.roadscanner.searchservice.testsupport.fakes.RecordingIndexReplayTrigger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RebuildIndexServiceTest {

    private final InMemorySearchableTripRepository repository = new InMemorySearchableTripRepository();
    private final RecordingIndexReplayTrigger replayTrigger = new RecordingIndexReplayTrigger();
    private final RebuildIndexService service = new RebuildIndexService(repository, replayTrigger);

    @Test
    void discardsTheIndexAndTriggersAReplay() {
        repository.save(SearchableTrip.publish(new TripId(UUID.randomUUID()), new OperatorId(UUID.randomUUID()), "Acme",
                new Route("Mumbai", "Pune"),
                new Schedule(Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z")),
                new BusType("AC Sleeper", List.of()),
                new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR")),
                Instant.parse("2026-07-01T00:00:00Z")));

        service.rebuild();

        assertThat(repository.isEmpty()).isTrue();
        assertThat(replayTrigger.triggerCount()).isEqualTo(1);
    }
}
