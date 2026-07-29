package com.roadscanner.searchservice.location.adapter.out.persistence;

import com.roadscanner.searchservice.location.domain.model.GeoCoordinates;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * Exercises {@link LocationRepositoryAdapter} against a real Postgres (Testcontainers) — the
 * query semantics {@code InMemoryLocationRepository} deliberately only approximates: case
 * handling under a real collation, the display-name-before-city ranking, the active-only filter
 * pushed into SQL, and the unique index on {@code google_place_id} actually firing.
 *
 * <p>Running against the container also validates V2 against the JPA entities: {@code ddl-auto:
 * validate} means a column this adapter maps differently from the migration fails the test
 * outright rather than at first deploy.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, LocationRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class LocationRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final LocationAddress HYDERABAD = new LocationAddress("Hyderabad", "Telangana", "India");
    private static final LocationAddress PUNE = new LocationAddress("Pune", "Maharashtra", "India");

    @Autowired
    private LocationRepositoryAdapter adapter;

    /**
     * Used to force a flush — the unique index fires on write, not on {@code save()} — and to
     * clear the persistence context, so a read afterwards really goes to Postgres instead of
     * handing back the instance already sitting in the first-level cache.
     */
    @Autowired
    private TestEntityManager entityManager;

    /** Forces the pending writes out and drops the first-level cache, so the next read is real. */
    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    private static Location location(String displayName, LocationAddress address) {
        return Location.create(LocationId.generate(), displayName, address, null, null, null, NOW);
    }

    private Location save(String displayName, LocationAddress address) {
        return adapter.save(location(displayName, address));
    }

    @Test
    void savesAndRoundTripsEveryField() {
        Location hyderabad = Location.create(LocationId.generate(), "Hyderabad", HYDERABAD,
                new GeoCoordinates(new BigDecimal("17.3850000"), new BigDecimal("78.4867000")),
                new GooglePlaceId("place-hyd"), "Asia/Kolkata", NOW);

        adapter.save(hyderabad);
        reload();

        Location found = adapter.findById(hyderabad.id()).orElseThrow();
        assertThat(found.displayName()).isEqualTo("Hyderabad");
        assertThat(found.address()).isEqualTo(HYDERABAD);
        assertThat(found.coordinates().orElseThrow().latitude()).isEqualByComparingTo("17.3850000");
        assertThat(found.coordinates().orElseThrow().longitude()).isEqualByComparingTo("78.4867000");
        assertThat(found.googlePlaceId()).contains(new GooglePlaceId("place-hyd"));
        assertThat(found.timezone()).contains("Asia/Kolkata");
        assertThat(found.isActive()).isTrue();
        assertThat(found.createdAt()).isEqualTo(NOW);
    }

    @Test
    void roundTripsALocationWithNoOptionalFields() {
        Location bare = location("Kachiguda", HYDERABAD);

        adapter.save(bare);
        reload();

        Location found = adapter.findById(bare.id()).orElseThrow();
        assertThat(found.coordinates()).isEmpty();
        assertThat(found.googlePlaceId()).isEmpty();
        assertThat(found.timezone()).isEmpty();
        assertThat(found.address().stateIfPresent()).contains("Telangana");
    }

    @Test
    void storesAnAbsentStateAsNull() {
        Location singapore = location("Singapore", new LocationAddress("Singapore", null, "Singapore"));

        adapter.save(singapore);
        reload();

        assertThat(adapter.findById(singapore.id()).orElseThrow().address().stateIfPresent()).isEmpty();
    }

    @Test
    void findByIdIsEmptyForAnUnknownId() {
        assertThat(adapter.findById(LocationId.generate())).isEmpty();
    }

    @Test
    void saveUpdatesTheExistingRowRatherThanInsertingASecondOne() {
        Location hyderabad = save("Hyderabad", HYDERABAD);

        hyderabad.update("Hyderabad Deccan", HYDERABAD, null, new GooglePlaceId("place-2"), "Asia/Kolkata",
                NOW.plusSeconds(60));
        adapter.save(hyderabad);
        reload();

        Location found = adapter.findById(hyderabad.id()).orElseThrow();
        assertThat(found.displayName()).isEqualTo("Hyderabad Deccan");
        assertThat(found.googlePlaceId()).contains(new GooglePlaceId("place-2"));
        assertThat(found.updatedAt()).isEqualTo(NOW.plusSeconds(60));
        // createdAt is fixed for the row's lifetime — an update must not rewrite it.
        assertThat(found.createdAt()).isEqualTo(NOW);
        assertThat(adapter.searchActiveByPrefix("Hyderabad", 25)).hasSize(1);
    }

    @Test
    void saveClearsAnOptionalFieldTheDomainDropped() {
        Location hyderabad = Location.create(LocationId.generate(), "Hyderabad", HYDERABAD,
                new GeoCoordinates(new BigDecimal("17.3850000"), new BigDecimal("78.4867000")),
                new GooglePlaceId("place-hyd"), "Asia/Kolkata", NOW);
        adapter.save(hyderabad);

        // Replace-not-patch: an update that omits coordinates must null the columns, not leave
        // the previous values behind.
        hyderabad.update("Hyderabad", HYDERABAD, null, null, null, NOW.plusSeconds(60));
        adapter.save(hyderabad);
        reload();

        Location found = adapter.findById(hyderabad.id()).orElseThrow();
        assertThat(found.coordinates()).isEmpty();
        assertThat(found.googlePlaceId()).isEmpty();
        assertThat(found.timezone()).isEmpty();
    }

    @Test
    void findByGooglePlaceIdResolvesTheOwningLocation() {
        Location hyderabad = Location.create(LocationId.generate(), "Hyderabad", HYDERABAD, null,
                new GooglePlaceId("place-hyd"), null, NOW);
        adapter.save(hyderabad);
        save("Pune", PUNE);
        reload();

        assertThat(adapter.findByGooglePlaceId(new GooglePlaceId("place-hyd")).orElseThrow().id())
                .isEqualTo(hyderabad.id());
        assertThat(adapter.findByGooglePlaceId(new GooglePlaceId("place-unknown"))).isEmpty();
    }

    @Test
    void theUniqueIndexRejectsASecondLocationWithTheSameGooglePlaceId() {
        adapter.save(Location.create(LocationId.generate(), "Hyderabad", HYDERABAD, null,
                new GooglePlaceId("place-hyd"), null, NOW));

        // The application layer pre-checks for a friendly 409, but the index is the real
        // guarantee under concurrency — this proves it exists and is enforced.
        assertThatThrownBy(() -> {
            adapter.save(Location.create(LocationId.generate(), "Hyderabad Deccan", HYDERABAD,
                    null, new GooglePlaceId("place-hyd"), null, NOW));
            entityManager.flush();
        }).hasStackTraceContaining("idx_location_google_place");
    }

    @Test
    void manyLocationsMayHaveNoGooglePlaceId() {
        Location first = save("Hyderabad", HYDERABAD);
        Location second = save("Kachiguda", HYDERABAD);

        // Postgres treats NULLs as distinct, so the unique index enforces "unique when present"
        // with no partial-index trickery — two unmatched locations coexist happily.
        assertThat(adapter.findById(first.id())).isPresent();
        assertThat(adapter.findById(second.id())).isPresent();
    }

    @Test
    void searchMatchesADisplayNamePrefixCaseInsensitively() {
        Location hyderabad = save("Hyderabad", HYDERABAD);
        save("Pune", PUNE);

        assertThat(adapter.searchActiveByPrefix("hYd", 25)).extracting(Location::id).containsExactly(hyderabad.id());
    }

    @Test
    void searchAlsoMatchesOnCityAndRanksDisplayNameHitsFirst() {
        save("MGBS", HYDERABAD);
        save("Hyderabad Deccan", HYDERABAD);
        save("Hyderabad", HYDERABAD);

        // Typing "hyd" should surface Hyderabad itself above a stop that merely sits in it,
        // then fall back to alphabetical for a stable list.
        assertThat(adapter.searchActiveByPrefix("hyd", 25)).extracting(Location::displayName)
                .containsExactly("Hyderabad", "Hyderabad Deccan", "MGBS");
    }

    @Test
    void searchExcludesSoftDeletedLocations() {
        Location withdrawn = location("Hyderguda", HYDERABAD);
        withdrawn.disable(NOW.plusSeconds(60));
        adapter.save(withdrawn);

        assertThat(adapter.searchActiveByPrefix("hyderg", 25)).isEmpty();
        // …but it stays resolvable by id, which is the whole point of a soft delete.
        assertThat(adapter.findById(withdrawn.id())).isPresent();
    }

    @Test
    void searchAppliesTheLimitInSql() {
        save("Hyderabad", HYDERABAD);
        save("Hyderabad Deccan", HYDERABAD);
        save("Hyderguda", HYDERABAD);

        assertThat(adapter.searchActiveByPrefix("hyd", 2)).hasSize(2);
    }

    @Test
    void searchReturnsNothingWhenNoLocationMatches() {
        save("Hyderabad", HYDERABAD);

        assertThat(adapter.searchActiveByPrefix("zzz", 25)).isEmpty();
    }
}
