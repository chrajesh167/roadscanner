package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.FareAmount;
import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.RouteId;
import com.roadscanner.inventoryservice.domain.model.SyncStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/** Exercises {@link ProviderMappingRepositoryAdapter} against a real Postgres (Testcontainers) —
 * in particular the {@code (providerType, providerTripId)} lookup that catalog synchronization's
 * reconciliation keys on (docs/services/inventory-service/domain-model.md's {@code ProviderMapping}
 * entry), which an in-memory fake exercises but cannot prove against the real unique constraint. */
@DataJpaTest
@Import({TestcontainersConfiguration.class, ProviderMappingRepositoryAdapter.class, TripRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProviderMappingRepositoryAdapterTest {

    private static final RouteId ROUTE_ID = new RouteId(java.util.UUID.fromString("33333333-3333-3333-3333-333333333303"));
    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

    @Autowired
    private ProviderMappingRepositoryAdapter adapter;

    @Autowired
    private TripRepositoryAdapter tripRepositoryAdapter;

    private TripId seedTrip() {
        TripId tripId = TripId.generate();
        Trip trip = Trip.createFromProviderSync(tripId, ROUTE_ID, "Chennai", "Bengaluru",
                new TripSchedule(NOW.plusSeconds(3600), NOW.plusSeconds(7200)), "Mock Travels", "AC Sleeper",
                new FareAmount(BigDecimal.valueOf(899), Currency.getInstance("INR"), NOW), NOW);
        tripRepositoryAdapter.save(trip);
        return tripId;
    }

    @Test
    void savesAndRoundTripsAMapping() {
        TripId tripId = seedTrip();
        adapter.save(ProviderMapping.create(tripId, new ProviderType("MOCK"), "MOCK-TRIP-1", NOW));

        ProviderMapping found = adapter.findByTripId(tripId).orElseThrow();
        assertThat(found.providerType()).isEqualTo(new ProviderType("MOCK"));
        assertThat(found.providerTripId()).isEqualTo("MOCK-TRIP-1");
        assertThat(found.syncStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void findByProviderTypeAndProviderTripIdLocatesTheMapping() {
        TripId tripId = seedTrip();
        adapter.save(ProviderMapping.create(tripId, new ProviderType("MOCK"), "MOCK-TRIP-1", NOW));

        ProviderMapping found = adapter.findByProviderTypeAndProviderTripId(new ProviderType("MOCK"), "MOCK-TRIP-1")
                .orElseThrow();

        assertThat(found.tripId()).isEqualTo(tripId);
    }

    @Test
    void findByProviderTypeAndProviderTripIdIsEmptyWhenUnknown() {
        assertThat(adapter.findByProviderTypeAndProviderTripId(new ProviderType("MOCK"), "UNKNOWN")).isEmpty();
    }

    @Test
    void savingAnExistingMappingUpdatesInPlace() {
        TripId tripId = seedTrip();
        ProviderMapping mapping = ProviderMapping.create(tripId, new ProviderType("MOCK"), "MOCK-TRIP-1", NOW);
        adapter.save(mapping);

        ProviderMapping reloaded = adapter.findByTripId(tripId).orElseThrow();
        reloaded.recordSync(NOW.plusSeconds(60), SyncStatus.FAILED);
        adapter.save(reloaded);

        ProviderMapping found = adapter.findByTripId(tripId).orElseThrow();
        assertThat(found.syncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(found.lastSyncedAt()).isEqualTo(NOW.plusSeconds(60));
    }
}
