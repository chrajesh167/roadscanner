package com.roadscanner.searchservice.location.adapter.out.persistence;

import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.model.ProviderPlaceRef;
import com.roadscanner.searchservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * Exercises {@link ProviderLocationMappingRepositoryAdapter} against a real Postgres — chiefly
 * the two things no in-memory fake can prove: that {@code JSONB} round-trips an opaque metadata
 * payload, and that {@code fk_provider_location} really refuses a mapping pointing at a location
 * that does not exist.
 *
 * <p>Also covers the reverse lookups Sprint 3 depends on ({@code provider} + city/station id),
 * so that sprint inherits a tested query path rather than an untested declaration.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, ProviderLocationMappingRepositoryAdapter.class,
        LocationRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProviderLocationMappingRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final ProviderCode FLIXBUS = new ProviderCode("FLIXBUS");
    private static final ProviderCode REDBUS = new ProviderCode("REDBUS");

    @Autowired
    private ProviderLocationMappingRepositoryAdapter adapter;

    @Autowired
    private LocationRepositoryAdapter locations;

    /**
     * Used to force a flush — database constraints fire on write, not on {@code save()} — and to
     * clear the persistence context, so a read afterwards really goes to Postgres instead of
     * handing back the instance already sitting in the first-level cache.
     */
    @Autowired
    private TestEntityManager entityManager;

    private Location hyderabad;
    private Location pune;

    @BeforeEach
    void seedCatalogue() {
        hyderabad = locations.save(Location.create(LocationId.generate(), "Hyderabad",
                new LocationAddress("Hyderabad", "Telangana", "India"), null, null, null, NOW));
        pune = locations.save(Location.create(LocationId.generate(), "Pune",
                new LocationAddress("Pune", "Maharashtra", "India"), null, null, null, NOW));
    }

    private ProviderLocationMapping mapping(ProviderCode provider, LocationId locationId, String cityId,
                                            String stationId, String stationName, String metadataJson) {
        return ProviderLocationMapping.create(ProviderLocationMappingId.generate(), provider, locationId,
                new ProviderPlaceRef(cityId, stationId, stationName), metadataJson, NOW);
    }

    /** Forces the pending writes out and drops the first-level cache, so the next read is real. */
    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void savesAndRoundTripsAMappingIncludingItsJsonbMetadata() {
        ProviderLocationMapping saved = adapter.save(mapping(FLIXBUS, hyderabad.id(), "58291", "station-1", "MGBS",
                "{\"platform\":\"7\",\"tz\":\"Asia/Kolkata\"}"));
        reload();

        ProviderLocationMapping found =
                adapter.findByLocationAndProvider(hyderabad.id(), FLIXBUS).orElseThrow();
        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.provider()).isEqualTo(FLIXBUS);
        assertThat(found.locationId()).isEqualTo(hyderabad.id());
        assertThat(found.placeRef().cityId()).isEqualTo("58291");
        assertThat(found.placeRef().stationId()).isEqualTo("station-1");
        assertThat(found.placeRef().stationName()).isEqualTo("MGBS");
        // JSONB is a parsed type, so Postgres hands back its own normalised rendering rather
        // than the exact bytes written — the payload survives, its whitespace does not.
        assertThat(found.metadataJson().orElseThrow())
                .contains("\"platform\"", "\"7\"", "\"tz\"", "\"Asia/Kolkata\"");
        assertThat(found.isVerified()).isFalse();
        assertThat(found.lastSynced()).isEmpty();
    }

    @Test
    void roundTripsAMappingWithNoMetadataAndNoStation() {
        adapter.save(mapping(REDBUS, pune.id(), "PNQ", null, null, null));
        reload();

        ProviderLocationMapping found = adapter.findByLocationAndProvider(pune.id(), REDBUS).orElseThrow();
        assertThat(found.metadataJson()).isEmpty();
        assertThat(found.placeRef().identifiesStation()).isFalse();
        assertThat(found.placeRef().cityId()).isEqualTo("PNQ");
    }

    @Test
    void findByLocationAndProviderIsEmptyWhenThatProviderDoesNotServeThePlace() {
        adapter.save(mapping(FLIXBUS, hyderabad.id(), "58291", "station-1", "MGBS", null));

        assertThat(adapter.findByLocationAndProvider(hyderabad.id(), REDBUS)).isEmpty();
    }

    @Test
    void findByLocationReturnsEveryProvidersViewOfOnePlace() {
        adapter.save(mapping(FLIXBUS, hyderabad.id(), "58291", "station-1", "MGBS", null));
        adapter.save(mapping(REDBUS, hyderabad.id(), "HYD", "rb-77", "Imliban", null));
        adapter.save(mapping(FLIXBUS, pune.id(), "41100", "station-9", "Swargate", null));

        assertThat(adapter.findByLocation(hyderabad.id()))
                .extracting(m -> m.provider().value())
                .containsExactlyInAnyOrder("FLIXBUS", "REDBUS");
    }

    @Test
    void findByLocationIsEmptyForAnUnmappedLocation() {
        assertThat(adapter.findByLocation(pune.id())).isEmpty();
    }

    @Test
    void resolvesAProvidersOwnCityIdBackToARoadScannerLocation() {
        adapter.save(mapping(FLIXBUS, hyderabad.id(), "58291", "station-1", "MGBS", null));
        // The same raw id under a different provider must not collide — the lookup is scoped by
        // provider, which is why idx_provider_city is composite.
        adapter.save(mapping(REDBUS, pune.id(), "58291", "rb-77", "Swargate", null));

        assertThat(adapter.findByProviderCityId(FLIXBUS, "58291").orElseThrow().locationId())
                .isEqualTo(hyderabad.id());
        assertThat(adapter.findByProviderCityId(REDBUS, "58291").orElseThrow().locationId())
                .isEqualTo(pune.id());
        assertThat(adapter.findByProviderCityId(FLIXBUS, "does-not-exist")).isEmpty();
    }

    @Test
    void resolvesAProvidersOwnStationIdBackToARoadScannerLocation() {
        adapter.save(mapping(FLIXBUS, hyderabad.id(), "58291", "station-1", "MGBS", null));

        assertThat(adapter.findByProviderStationId(FLIXBUS, "station-1").orElseThrow().locationId())
                .isEqualTo(hyderabad.id());
        assertThat(adapter.findByProviderStationId(REDBUS, "station-1")).isEmpty();
    }

    @Test
    void saveUpdatesTheExistingRowRatherThanInsertingASecondOne() {
        ProviderLocationMapping saved = adapter.save(
                mapping(FLIXBUS, hyderabad.id(), "58291", "station-1", "MGBS", null));

        saved.recordSync(new ProviderPlaceRef("58291", "station-2", "MGBS Bay 4"), "{\"bay\":4}",
                NOW.plusSeconds(3600));
        adapter.save(saved);
        reload();

        assertThat(adapter.findByLocation(hyderabad.id())).hasSize(1);
        ProviderLocationMapping found = adapter.findByLocationAndProvider(hyderabad.id(), FLIXBUS).orElseThrow();
        assertThat(found.placeRef().stationId()).isEqualTo("station-2");
        assertThat(found.metadataJson().orElseThrow()).contains("\"bay\"", "4");
        assertThat(found.lastSynced()).contains(NOW.plusSeconds(3600));
        assertThat(found.createdAt()).isEqualTo(NOW);
    }

    @Test
    void persistsTheVerifiedFlag() {
        ProviderLocationMapping saved = adapter.save(
                mapping(FLIXBUS, hyderabad.id(), "58291", "station-1", "MGBS", null));

        saved.markVerified(NOW.plusSeconds(60));
        adapter.save(saved);
        reload();

        assertThat(adapter.findByLocationAndProvider(hyderabad.id(), FLIXBUS).orElseThrow().isVerified()).isTrue();
    }

    @Test
    void theForeignKeyRejectsAMappingForAnUnknownLocation() {
        // A mapping must point at a real catalogue entry — otherwise a provider id could be
        // "translated" into a location nobody can resolve.
        assertThatThrownBy(() -> {
            adapter.save(mapping(FLIXBUS, LocationId.generate(), "58291", "station-1", "MGBS", null));
            entityManager.flush();
        }).hasStackTraceContaining("fk_provider_location");
    }
}
