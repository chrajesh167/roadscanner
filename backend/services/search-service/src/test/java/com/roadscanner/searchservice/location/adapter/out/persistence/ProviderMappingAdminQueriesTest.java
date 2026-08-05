package com.roadscanner.searchservice.location.adapter.out.persistence;

import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.model.ProviderPlaceRef;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;
import com.roadscanner.searchservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * The Sprint 5B administrative query paths against a real Postgres.
 *
 * <p>Two things here can only be proved against a real database. The first is V5's unique indexes:
 * they are the actual guarantee behind the three mapping rules, and an in-memory fake asserting a
 * pre-check would say nothing about whether the constraint exists. The second is the cross-table
 * work — the search that joins to {@code location}, and the {@code NOT EXISTS} anti-join behind the
 * unmapped-locations worklist — neither of which a single-table fake can model honestly.
 *
 * <p><strong>The schema arrives already populated.</strong> V4 seeds two canonical locations and
 * their FlixBus mappings, so this test never asserts against the table as a whole. Its fixtures use
 * provider codes and display names the seed cannot produce, and every assertion is scoped to them.
 * A test that assumed an empty table would pass only until someone added a seed row.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, ProviderLocationMappingRepositoryAdapter.class,
        LocationRepositoryAdapter.class})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProviderMappingAdminQueriesTest {

    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    /** Deliberately not FLIXBUS: V4 seeds FlixBus mappings, and reusing that code would make every
     * provider-scoped assertion depend on the seed's contents. */
    private static final ProviderCode ACME = new ProviderCode("ACMEBUS");
    private static final ProviderCode ZENITH = new ProviderCode("ZENITHRAIL");

    @Autowired
    private ProviderLocationMappingRepositoryAdapter adapter;

    @Autowired
    private LocationRepositoryAdapter locations;

    @Autowired
    private TestEntityManager entityManager;

    private Location alpha;
    private Location beta;
    private Location gamma;

    @BeforeEach
    void seedCatalogue() {
        // Names the V4 seed cannot produce, so the unmapped-locations assertions can filter to
        // exactly this fixture.
        alpha = locations.save(location("Zzalpha Junction", "Zzalpha"));
        beta = locations.save(location("Zzbeta Central", "Zzbeta"));
        gamma = locations.save(location("Zzgamma Depot", "Zzgamma"));
    }

    private static Location location(String displayName, String city) {
        return Location.create(LocationId.generate(), displayName,
                new LocationAddress(city, "Telangana", "India"), null, null, null, NOW);
    }

    private ProviderLocationMapping save(ProviderCode provider, LocationId locationId, String cityId,
                                         String stationId, String stationName) {
        return adapter.save(ProviderLocationMapping.create(ProviderLocationMappingId.generate(), provider,
                locationId, new ProviderPlaceRef(cityId, stationId, stationName), null, NOW));
    }

    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    /** Everything this fixture's providers can see — the seed uses neither code. */
    private List<ProviderLocationMapping> mappingsFor(ProviderCode provider) {
        return adapter.search(new ProviderLocationMappingRepository.Criteria(provider, null, null), 0, 50)
                .content();
    }

    /** Unmapped locations, narrowed to this fixture's three. */
    private List<String> unmappedFixtureNames(ProviderCode provider) {
        return locations.findActiveWithoutMappingForProvider(provider, "zz", 50).stream()
                .map(Location::displayName)
                .toList();
    }

    // ---------- V5: the constraints themselves ----------

    @Test
    void theDatabaseRefusesASecondMappingForTheSameLocationAndProvider() {
        save(ACME, alpha.id(), "city-1", null, null);
        save(ACME, alpha.id(), "city-2", null, null);

        // The application layer checks this first and returns a clean 409. This asserts the
        // backstop underneath it, which is what holds when two requests race past that check.
        assertThatThrownBy(this::reload).isInstanceOf(Exception.class);
    }

    @Test
    void theDatabaseRefusesTwoLocationsSharingOneProviderCityId() {
        save(ACME, alpha.id(), "shared-city", null, null);
        save(ACME, beta.id(), "shared-city", null, null);

        assertThatThrownBy(this::reload).isInstanceOf(Exception.class);
    }

    @Test
    void theDatabaseRefusesTwoLocationsSharingOneProviderStationId() {
        save(ACME, alpha.id(), null, "shared-station", null);
        save(ACME, beta.id(), null, "shared-station", null);

        assertThatThrownBy(this::reload).isInstanceOf(Exception.class);
    }

    @Test
    void theConstraintsAreScopedPerProvider() {
        save(ACME, alpha.id(), "42", null, null);
        save(ZENITH, alpha.id(), "42", null, null);

        // Provider ids are opaque and namespaced by provider. Two providers using the same string
        // is a coincidence, not a conflict — and both may describe the same canonical place.
        reload();
        assertThat(adapter.findByLocation(alpha.id())).hasSize(2);
    }

    @Test
    void severalStationLessMappingsCoexistBecauseNullsAreDistinct() {
        save(ACME, alpha.id(), "city-1", null, null);
        save(ACME, beta.id(), "city-2", null, null);
        save(ACME, gamma.id(), "city-3", null, null);

        // If the unique index treated NULL as a value, a provider could hold exactly one mapping
        // with no station id across the whole platform. Postgres treats NULLs as distinct, which
        // is precisely why these are plain unique indexes rather than partial ones.
        reload();
        assertThat(mappingsFor(ACME)).hasSize(3);
    }

    // ---------- Search ----------

    @Test
    void filtersByProvider() {
        save(ACME, alpha.id(), "city-1", null, null);
        save(ZENITH, beta.id(), "city-2", null, null);
        reload();

        assertThat(mappingsFor(ZENITH)).singleElement()
                .extracting(mapping -> mapping.locationId()).isEqualTo(beta.id());
    }

    @Test
    void filtersByVerified() {
        ProviderLocationMapping unverified = save(ACME, alpha.id(), "city-1", null, null);
        ProviderLocationMapping verified = save(ACME, beta.id(), "city-2", null, null);
        verified.markVerified(NOW);
        adapter.save(verified);
        reload();

        assertThat(adapter.search(new ProviderLocationMappingRepository.Criteria(ACME, true, null), 0, 50)
                .content()).singleElement()
                .extracting(ProviderLocationMapping::id).isEqualTo(verified.id());

        assertThat(adapter.search(new ProviderLocationMappingRepository.Criteria(ACME, false, null), 0, 50)
                .content()).singleElement()
                .extracting(ProviderLocationMapping::id).isEqualTo(unverified.id());
    }

    @Test
    void searchTermReachesTheCanonicalLocationsDisplayNameAndCity() {
        save(ACME, alpha.id(), "opaque-1", null, null);
        save(ACME, beta.id(), "opaque-2", null, null);
        reload();

        // The operator types the place they know, not the provider's id — which is only possible
        // because the query joins to the location table.
        assertThat(searchTerm("zzalpha")).singleElement()
                .extracting(ProviderLocationMapping::locationId).isEqualTo(alpha.id());
    }

    @Test
    void searchTermAlsoMatchesEveryProviderSideField() {
        save(ACME, alpha.id(), "city-abc", "station-xyz", "MGBS Terminal");
        save(ACME, beta.id(), "city-def", null, null);
        reload();

        assertThat(searchTerm("XYZ")).hasSize(1);
        assertThat(searchTerm("mgbs")).hasSize(1);
        assertThat(searchTerm("city-abc")).hasSize(1);
    }

    private List<ProviderLocationMapping> searchTerm(String term) {
        return adapter.search(new ProviderLocationMappingRepository.Criteria(ACME, null, term), 0, 50).content();
    }

    @Test
    void filtersCombineWithAnd() {
        save(ACME, alpha.id(), "city-1", null, null);
        save(ZENITH, alpha.id(), "city-2", null, null);
        reload();

        assertThat(adapter.search(new ProviderLocationMappingRepository.Criteria(ACME, null, "zzalpha"), 0, 50)
                .content()).hasSize(1);
        // Same provider, same term, but nothing is verified — AND, not OR.
        assertThat(adapter.search(new ProviderLocationMappingRepository.Criteria(ACME, true, "zzalpha"), 0, 50)
                .content()).isEmpty();
    }

    @Test
    void pagesAndReportsTheTotalAcrossPages() {
        save(ACME, alpha.id(), "city-1", null, null);
        save(ACME, beta.id(), "city-2", null, null);
        save(ACME, gamma.id(), "city-3", null, null);
        reload();

        ProviderLocationMappingRepository.Page first = adapter.search(
                new ProviderLocationMappingRepository.Criteria(ACME, null, null), 0, 2);

        assertThat(first.content()).hasSize(2);
        // The total is the whole result set, not the page — a pager cannot be drawn from a count
        // of what it can already see.
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);

        assertThat(adapter.search(new ProviderLocationMappingRepository.Criteria(ACME, null, null), 1, 2)
                .content()).hasSize(1);
    }

    @Test
    void aBlankSearchTermIsTreatedAsNoFilter() {
        save(ACME, alpha.id(), "city-1", null, null);
        reload();

        assertThat(adapter.search(new ProviderLocationMappingRepository.Criteria(ACME, null, "   "), 0, 50)
                .content()).hasSize(1);
    }

    @Test
    void aTermContainingWildcardCharactersMatchesLiterally() {
        save(ACME, alpha.id(), "city_1", null, null);
        save(ACME, beta.id(), "cityX1", null, null);
        reload();

        // Unescaped, `_` is a single-character wildcard and this would match both rows.
        assertThat(searchTerm("city_1")).singleElement()
                .extracting(ProviderLocationMapping::locationId).isEqualTo(alpha.id());
    }

    // ---------- findById / delete ----------

    @Test
    void findsAMappingByItsOwnId() {
        ProviderLocationMapping saved = save(ACME, alpha.id(), "city-1", null, null);
        reload();

        assertThat(adapter.findById(saved.id())).isPresent()
                .get().extracting(mapping -> mapping.placeRef().cityId()).isEqualTo("city-1");
    }

    @Test
    void findByIdIsEmptyForAnUnknownMapping() {
        assertThat(adapter.findById(ProviderLocationMappingId.generate())).isEmpty();
    }

    @Test
    void deletesAMappingAndFreesItsUniqueKeys() {
        ProviderLocationMapping saved = save(ACME, alpha.id(), "city-1", null, null);
        reload();

        adapter.deleteById(saved.id());
        reload();

        assertThat(adapter.findById(saved.id())).isEmpty();
        // A hard delete genuinely releases the unique keys, so the same provider id can be
        // re-mapped to a different place — which is what correcting a wrong mapping requires.
        save(ACME, beta.id(), "city-1", null, null);
        reload();
        assertThat(adapter.findByProviderCityId(ACME, "city-1")).isPresent()
                .get().extracting(ProviderLocationMapping::locationId).isEqualTo(beta.id());
    }

    @Test
    void deletingAnAbsentMappingIsANoOp() {
        adapter.deleteById(ProviderLocationMappingId.generate());
        reload();

        assertThat(mappingsFor(ACME)).isEmpty();
    }

    // ---------- Unmapped locations ----------

    @Test
    void listsCanonicalLocationsWithNoMappingForTheProvider() {
        save(ACME, alpha.id(), "city-1", null, null);
        reload();

        assertThat(unmappedFixtureNames(ACME))
                .containsExactly("Zzbeta Central", "Zzgamma Depot")
                .doesNotContain("Zzalpha Junction");
    }

    @Test
    void unmappedIsAnsweredPerProvider() {
        save(ACME, alpha.id(), "city-1", null, null);
        reload();

        // Mapped for one provider is still unmapped for another — the worklist is per provider,
        // which is the entire reason onboarding a second provider needs no code change.
        assertThat(unmappedFixtureNames(ZENITH))
                .containsExactly("Zzalpha Junction", "Zzbeta Central", "Zzgamma Depot");
    }

    @Test
    void unmappedExcludesWithdrawnLocations() {
        Location retired = locations.save(location("Zzdelta Old Depot", "Zzdelta"));
        retired.disable(NOW);
        locations.save(retired);
        reload();

        // A soft-deleted place should not be offered as work to do.
        assertThat(unmappedFixtureNames(ACME)).doesNotContain("Zzdelta Old Depot");
    }

    @Test
    void unmappedNarrowsByFreeTextAndRespectsTheLimit() {
        reload();

        assertThat(locations.findActiveWithoutMappingForProvider(ACME, "zzbeta", 50))
                .extracting(Location::displayName).containsExactly("Zzbeta Central");
        assertThat(locations.findActiveWithoutMappingForProvider(ACME, "zz", 2)).hasSize(2);
    }

    @Test
    void unmappedTreatsWildcardCharactersInTheTermLiterally() {
        locations.save(location("Zzepsilon_1 Stand", "Zzepsilon"));
        locations.save(location("ZzepsilonX1 Stand", "Zzepsilon"));
        reload();

        // Unescaped, `_` is a single-character wildcard and this would match both stands.
        assertThat(locations.findActiveWithoutMappingForProvider(ACME, "zzepsilon_1", 50))
                .extracting(Location::displayName).containsExactly("Zzepsilon_1 Stand");
    }
}
