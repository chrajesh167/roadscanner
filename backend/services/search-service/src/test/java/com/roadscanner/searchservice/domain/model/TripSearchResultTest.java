package com.roadscanner.searchservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripSearchResultTest {

    @Test
    void rejectsNullFields() {
        SearchableTrip trip = SearchableTrip.publish(
                new TripId(java.util.UUID.randomUUID()), new OperatorId(java.util.UUID.randomUUID()), "Acme Travels",
                new Route("Mumbai", "Pune"),
                new Schedule(Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z")),
                new BusType("AC Sleeper", List.of()),
                new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR")),
                Instant.parse("2026-07-01T00:00:00Z"));

        assertThatThrownBy(() -> new TripSearchResult(null, AvailabilityStatus.unknown()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TripSearchResult(trip, null))
                .isInstanceOf(NullPointerException.class);
    }
}
