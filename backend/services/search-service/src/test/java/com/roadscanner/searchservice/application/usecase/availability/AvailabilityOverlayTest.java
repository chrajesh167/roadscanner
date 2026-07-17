package com.roadscanner.searchservice.application.usecase.availability;

import com.roadscanner.searchservice.domain.model.AvailabilityStatus;
import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.model.TripSearchResult;
import com.roadscanner.searchservice.testsupport.fakes.InMemoryAvailabilityCache;
import com.roadscanner.searchservice.testsupport.fakes.StubAvailabilityClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityOverlayTest {

    private final InMemoryAvailabilityCache cache = new InMemoryAvailabilityCache();
    private final StubAvailabilityClient client = new StubAvailabilityClient();
    private final AvailabilityOverlay overlay = new AvailabilityOverlay(cache, client);

    private SearchableTrip trip(TripId tripId) {
        return SearchableTrip.publish(tripId, new OperatorId(UUID.randomUUID()), "Acme",
                new Route("Mumbai", "Pune"),
                new Schedule(Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z")),
                new BusType("AC Sleeper", List.of()),
                new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR")),
                Instant.parse("2026-07-01T00:00:00Z"));
    }

    @Test
    void cacheHitSkipsTheLiveCall() {
        TripId tripId = new TripId(UUID.randomUUID());
        cache.put(tripId, AvailabilityStatus.of(7));

        TripSearchResult result = overlay.overlay(trip(tripId));

        assertThat(result.availability()).isEqualTo(AvailabilityStatus.of(7));
        assertThat(client.callCount()).isZero();
    }

    @Test
    void cacheMissFallsThroughToTheLiveCallAndCachesAKnownResult() {
        TripId tripId = new TripId(UUID.randomUUID());
        client.willReturn(tripId, AvailabilityStatus.of(3));

        TripSearchResult result = overlay.overlay(trip(tripId));

        assertThat(result.availability()).isEqualTo(AvailabilityStatus.of(3));
        assertThat(client.callCount()).isEqualTo(1);
        assertThat(cache.wasCached(tripId)).isTrue();
    }

    @Test
    void anUnknownResultIsNeverCached() {
        TripId tripId = new TripId(UUID.randomUUID());
        // StubAvailabilityClient defaults to unknown() for any trip with no configured response.

        TripSearchResult result = overlay.overlay(trip(tripId));

        assertThat(result.availability().isKnown()).isFalse();
        assertThat(cache.wasCached(tripId)).isFalse();
    }
}
