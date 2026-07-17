package com.roadscanner.searchservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the two real invariants this projection encodes — staleness rejection and
 * terminal-state rejection — per {@link SearchableTrip}'s own Javadoc.
 */
class SearchableTripTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final TripId TRIP_ID = new TripId(UUID.randomUUID());
    private static final OperatorId OPERATOR_ID = new OperatorId(UUID.randomUUID());
    private static final Route ROUTE = new Route("Mumbai", "Pune");
    private static final Schedule SCHEDULE = new Schedule(
            Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"));
    private static final BusType BUS_TYPE = new BusType("AC Sleeper", List.of("WiFi"));
    private static final FareSnapshot FARE = new FareSnapshot(BigDecimal.valueOf(500), Currency.getInstance("INR"));

    private SearchableTrip published() {
        return SearchableTrip.publish(TRIP_ID, OPERATOR_ID, "Acme Travels", ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT);
    }

    @Test
    void publishStartsBookableWithNoRating() {
        SearchableTrip trip = published();

        assertThat(trip.bookable()).isTrue();
        assertThat(trip.rating()).isEqualTo(RatingSnapshot.none());
        assertThat(trip.createdAt()).isEqualTo(PUBLISHED_AT);
        assertThat(trip.lastTripEventAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void applyUpdateOverwritesFieldsAndAdvancesFreshnessMarker() {
        SearchableTrip trip = published();
        FareSnapshot newFare = new FareSnapshot(BigDecimal.valueOf(600), Currency.getInstance("INR"));
        Instant updateTime = PUBLISHED_AT.plusSeconds(60);

        boolean applied = trip.applyUpdate("Acme Travels Pvt Ltd", ROUTE, SCHEDULE, BUS_TYPE, newFare, updateTime);

        assertThat(applied).isTrue();
        assertThat(trip.operatorName()).isEqualTo("Acme Travels Pvt Ltd");
        assertThat(trip.fare()).isEqualTo(newFare);
        assertThat(trip.lastTripEventAt()).isEqualTo(updateTime);
    }

    @Test
    void applyUpdateRejectsStaleRedeliveryAsANoOp() {
        SearchableTrip trip = published();
        trip.applyUpdate("Second Name", ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT.plusSeconds(60));

        boolean appliedStale = trip.applyUpdate("Stale Name", ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT.plusSeconds(30));

        assertThat(appliedStale).isFalse();
        assertThat(trip.operatorName()).isEqualTo("Second Name");
    }

    @Test
    void applyUpdateRejectsExactDuplicateTimestampAsStale() {
        SearchableTrip trip = published();
        // occurredAt must be strictly AFTER lastTripEventAt — an exact-timestamp redelivery of
        // the same event is indistinguishable from a stale one and must not re-apply.
        boolean applied = trip.applyUpdate("Renamed", ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT);

        assertThat(applied).isFalse();
        assertThat(trip.operatorName()).isEqualTo("Acme Travels");
    }

    @Test
    void cancelIsUnconditionalAndIdempotent() {
        SearchableTrip trip = published();

        boolean firstCancel = trip.cancel(PUBLISHED_AT.plusSeconds(10));
        boolean secondCancel = trip.cancel(PUBLISHED_AT.plusSeconds(20));

        assertThat(firstCancel).isTrue();
        assertThat(secondCancel).isFalse();
        assertThat(trip.bookable()).isFalse();
    }

    @Test
    void cancelAppliesEvenWithAnEarlierTimestampThanTheLastTripEvent() {
        SearchableTrip trip = published();
        trip.applyUpdate("Renamed", ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT.plusSeconds(100));

        // Cancellation is treated as authoritative regardless of ordering relative to updates —
        // see the aggregate's Javadoc for why (no "un-cancel" event exists on the platform).
        boolean applied = trip.cancel(PUBLISHED_AT.plusSeconds(50));

        assertThat(applied).isTrue();
        assertThat(trip.bookable()).isFalse();
    }

    @Test
    void applyUpdateAfterCancellationIsRejectedAsTerminal() {
        SearchableTrip trip = published();
        trip.cancel(PUBLISHED_AT.plusSeconds(10));

        boolean applied = trip.applyUpdate("Resurrected Name", ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT.plusSeconds(20));

        assertThat(applied).isFalse();
        assertThat(trip.bookable()).isFalse();
        assertThat(trip.operatorName()).isEqualTo("Acme Travels");
    }

    @Test
    void applyRatingUpdateAdvancesIndependentlyOfTripEvents() {
        SearchableTrip trip = published();
        RatingSnapshot newRating = new RatingSnapshot(4.5, 10);

        boolean applied = trip.applyRatingUpdate(newRating, PUBLISHED_AT.plusSeconds(5));

        assertThat(applied).isTrue();
        assertThat(trip.rating()).isEqualTo(newRating);
    }

    @Test
    void applyRatingUpdateRejectsStaleRedelivery() {
        SearchableTrip trip = published();
        RatingSnapshot laterRating = new RatingSnapshot(4.5, 10);
        trip.applyRatingUpdate(laterRating, PUBLISHED_AT.plusSeconds(60));

        RatingSnapshot staleRating = new RatingSnapshot(3.0, 5);
        boolean applied = trip.applyRatingUpdate(staleRating, PUBLISHED_AT.plusSeconds(30));

        assertThat(applied).isFalse();
        assertThat(trip.rating()).isEqualTo(laterRating);
    }

    @Test
    void ratingUpdatesDoNotAffectTripEventFreshnessAndViceVersa() {
        SearchableTrip trip = published();
        trip.applyRatingUpdate(new RatingSnapshot(4.5, 10), PUBLISHED_AT.plusSeconds(1000));

        // A trip update with a timestamp between publish and the (later) rating update must
        // still be accepted — the two freshness markers are independent.
        boolean applied = trip.applyUpdate("Renamed", ROUTE, SCHEDULE, BUS_TYPE, FARE, PUBLISHED_AT.plusSeconds(60));

        assertThat(applied).isTrue();
    }

    @Test
    void equalityIsByTripIdOnly() {
        SearchableTrip trip = published();
        SearchableTrip differentState = SearchableTrip.reconstitute(TRIP_ID, OPERATOR_ID, "Different Name",
                ROUTE, SCHEDULE, BUS_TYPE, FARE, false, new RatingSnapshot(2.0, 3),
                PUBLISHED_AT, PUBLISHED_AT, PUBLISHED_AT);

        assertThat(trip).isEqualTo(differentState);
        assertThat(trip.hashCode()).isEqualTo(differentState.hashCode());
    }
}
