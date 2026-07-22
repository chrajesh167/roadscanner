package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.FareAmount;
import com.roadscanner.inventoryservice.domain.model.RouteId;
import com.roadscanner.inventoryservice.domain.model.Trip;
import com.roadscanner.inventoryservice.domain.model.TripId;
import com.roadscanner.inventoryservice.domain.model.TripSchedule;
import com.roadscanner.inventoryservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/** Exercises {@link TripRepositoryAdapter} against a real Postgres (Testcontainers) — in
 * particular the fetch-then-mutate {@code save} path that an in-memory fake cannot verify (see
 * that method's Javadoc on the optimistic-locking rationale). */
@DataJpaTest
@Import({TestcontainersConfiguration.class, TripRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class TripRepositoryAdapterTest {

    private static final RouteId ROUTE_ID = new RouteId(UUID.fromString("33333333-3333-3333-3333-333333333301"));
    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

    @Autowired
    private TripRepositoryAdapter adapter;

    private Trip firstPartyTrip(TripId id, UUID operatorId, Instant occurredAt) {
        return Trip.ingestFirstParty(id, ROUTE_ID, "Mumbai", "Pune",
                new TripSchedule(NOW.plusSeconds(3600), NOW.plusSeconds(7200)), operatorId, "Acme Travels",
                UUID.randomUUID(), "AC Sleeper", List.of("WiFi"),
                new FareAmount(BigDecimal.valueOf(500), Currency.getInstance("INR"), occurredAt), occurredAt);
    }

    @Test
    void savesAndRoundTripsANewTrip() {
        Trip trip = firstPartyTrip(TripId.generate(), UUID.randomUUID(), NOW);

        adapter.save(trip);

        Trip found = adapter.findById(trip.id()).orElseThrow();
        assertThat(found.origin()).isEqualTo("Mumbai");
        assertThat(found.destination()).isEqualTo("Pune");
        assertThat(found.fare().amount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(found.amenities()).containsExactly("WiFi");
        assertThat(found.bookable()).isTrue();
    }

    @Test
    void findByIdIsEmptyForAnUnknownId() {
        assertThat(adapter.findById(TripId.generate())).isEmpty();
    }

    @Test
    void savingAnExistingTripUpdatesInPlaceRatherThanInserting() {
        TripId tripId = TripId.generate();
        Trip trip = firstPartyTrip(tripId, UUID.randomUUID(), NOW);
        adapter.save(trip);

        Trip reloaded = adapter.findById(tripId).orElseThrow();
        reloaded.applyUpdate(ROUTE_ID, "Mumbai", "Pune", new TripSchedule(NOW.plusSeconds(3600), NOW.plusSeconds(7200)),
                "Acme Travels", "AC Sleeper", List.of("WiFi", "Charging Port"),
                new FareAmount(BigDecimal.valueOf(600), Currency.getInstance("INR"), NOW.plusSeconds(10)),
                NOW.plusSeconds(10));
        adapter.save(reloaded);

        Trip found = adapter.findById(tripId).orElseThrow();
        assertThat(found.fare().amount()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(found.amenities()).containsExactlyInAnyOrder("WiFi", "Charging Port");
    }

    @Test
    void findByOperatorIdReturnsAllTripsForThatOperator() {
        UUID operatorId = UUID.randomUUID();
        Trip tripOne = firstPartyTrip(TripId.generate(), operatorId, NOW);
        Trip tripTwo = firstPartyTrip(TripId.generate(), operatorId, NOW);
        Trip otherOperatorTrip = firstPartyTrip(TripId.generate(), UUID.randomUUID(), NOW);
        adapter.save(tripOne);
        adapter.save(tripTwo);
        adapter.save(otherOperatorTrip);

        List<Trip> found = adapter.findByOperatorId(operatorId);

        assertThat(found).extracting(Trip::id).containsExactlyInAnyOrder(tripOne.id(), tripTwo.id());
    }
}
