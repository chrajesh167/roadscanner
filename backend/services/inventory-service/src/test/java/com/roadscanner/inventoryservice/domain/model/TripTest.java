package com.roadscanner.inventoryservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the idempotent state-transition invariants — matching {@code search-service}'s
 * {@code SearchableTrip} pattern exactly (staleness rejection, terminal cancellation). */
class TripTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final TripId TRIP_ID = new TripId(UUID.randomUUID());
    private static final TripSchedule SCHEDULE = new TripSchedule(
            Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T12:00:00Z"));
    private static final FareAmount FARE = new FareAmount(BigDecimal.valueOf(500), Currency.getInstance("INR"), PUBLISHED_AT);

    private Trip ingested() {
        return Trip.ingestFirstParty(TRIP_ID, null, "Mumbai", "Pune", SCHEDULE, UUID.randomUUID(), "Acme Travels",
                null, "AC Sleeper", List.of("WiFi"), FARE, PUBLISHED_AT);
    }

    @Test
    void ingestFirstPartyStartsBookable() {
        Trip trip = ingested();

        assertThat(trip.bookable()).isTrue();
        assertThat(trip.supplyOrigin()).isEqualTo(SupplyOrigin.FIRST_PARTY);
        assertThat(trip.createdAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void applyUpdateOverwritesFieldsAndAdvancesFreshnessMarker() {
        Trip trip = ingested();
        FareAmount newFare = new FareAmount(BigDecimal.valueOf(600), Currency.getInstance("INR"), PUBLISHED_AT);
        Instant updateTime = PUBLISHED_AT.plusSeconds(60);

        boolean applied = trip.applyUpdate(null, "Mumbai", "Pune", SCHEDULE, "Acme Travels Pvt Ltd", "AC Sleeper",
                List.of("WiFi"), newFare, updateTime);

        assertThat(applied).isTrue();
        assertThat(trip.operatorDisplayName()).isEqualTo("Acme Travels Pvt Ltd");
        assertThat(trip.fare()).isEqualTo(newFare);
        assertThat(trip.lastEventAt()).isEqualTo(updateTime);
    }

    @Test
    void applyUpdateRejectsStaleRedeliveryAsANoOp() {
        Trip trip = ingested();
        trip.applyUpdate(null, "Mumbai", "Pune", SCHEDULE, "Second Name", "AC Sleeper", List.of(), FARE,
                PUBLISHED_AT.plusSeconds(60));

        boolean appliedStale = trip.applyUpdate(null, "Mumbai", "Pune", SCHEDULE, "Stale Name", "AC Sleeper",
                List.of(), FARE, PUBLISHED_AT.plusSeconds(10));

        assertThat(appliedStale).isFalse();
        assertThat(trip.operatorDisplayName()).isEqualTo("Second Name");
    }

    @Test
    void cancelIsIdempotentAndTerminal() {
        Trip trip = ingested();

        assertThat(trip.cancel(PUBLISHED_AT.plusSeconds(10))).isTrue();
        assertThat(trip.bookable()).isFalse();
        assertThat(trip.cancel(PUBLISHED_AT.plusSeconds(20))).isFalse();
    }

    @Test
    void applyUpdateRejectsAnAlreadyCancelledTrip() {
        Trip trip = ingested();
        trip.cancel(PUBLISHED_AT.plusSeconds(10));

        boolean applied = trip.applyUpdate(null, "Mumbai", "Pune", SCHEDULE, "New Name", "AC Sleeper", List.of(),
                FARE, PUBLISHED_AT.plusSeconds(20));

        assertThat(applied).isFalse();
    }

    @Test
    void createFromProviderSyncHasNoOperatorIdOrBusId() {
        Trip trip = Trip.createFromProviderSync(TripId.generate(), null, "Chennai", "Bengaluru", SCHEDULE,
                "FlixBus Partner", "Non-AC Seater", FARE, PUBLISHED_AT);

        assertThat(trip.operatorId()).isEmpty();
        assertThat(trip.busId()).isEmpty();
        assertThat(trip.supplyOrigin()).isEqualTo(SupplyOrigin.PROVIDER_SYNCED);
    }
}
