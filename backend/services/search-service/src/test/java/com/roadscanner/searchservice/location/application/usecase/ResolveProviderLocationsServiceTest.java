package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.model.ProviderPlaceRef;
import com.roadscanner.searchservice.location.domain.port.in.ResolveProviderLocations;
import com.roadscanner.searchservice.location.testsupport.InMemoryProviderLocationMappingRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The translation {@code inventory-service}'s catalog sync depends on.
 *
 * <p>Every case here is one a provider would otherwise reject or, worse, accept against the wrong
 * city. The rule is fail-closed throughout: anything this service cannot translate with confidence
 * is reported unresolved rather than substituted.
 */
class ResolveProviderLocationsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private static final ProviderCode FLIXBUS = new ProviderCode("FLIXBUS");

    private final InMemoryProviderLocationMappingRepository repository =
            new InMemoryProviderLocationMappingRepository();
    private final ResolveProviderLocationsService service = new ResolveProviderLocationsService(repository);

    private LocationId mapped(String cityId, boolean verified) {
        LocationId locationId = new LocationId(UUID.randomUUID());
        ProviderLocationMapping mapping = ProviderLocationMapping.create(ProviderLocationMappingId.generate(),
                FLIXBUS, locationId, new ProviderPlaceRef(cityId, null, null), null, NOW);
        if (verified) {
            mapping.markVerified(NOW);
        }
        repository.save(mapping);
        return locationId;
    }

    @Test
    void resolvesAVerifiedCityMappingToTheProvidersOwnCityId() {
        LocationId hyderabad = mapped("3da253ae-02ca-430c-87e5-22842065a77d", true);

        ResolveProviderLocations.Result result = service.resolve(
                new ResolveProviderLocations.Query(FLIXBUS, List.of(hyderabad)));

        assertThat(result.cityIdsByLocation()).containsExactly(
                org.assertj.core.api.Assertions.entry(hyderabad, "3da253ae-02ca-430c-87e5-22842065a77d"));
        assertThat(result.unresolved()).isEmpty();
    }

    @Test
    void refusesAnUnverifiedMapping() {
        // An unverified mapping is a claim nobody has confirmed. Acting on one here imports a trip
        // into the catalog as bookable, and a wrong city id sells a real seat in the wrong place.
        LocationId unverified = mapped("possibly-wrong-city-id", false);

        ResolveProviderLocations.Result result = service.resolve(
                new ResolveProviderLocations.Query(FLIXBUS, List.of(unverified)));

        assertThat(result.cityIdsByLocation()).isEmpty();
        assertThat(result.unresolved()).containsExactly(unverified);
    }

    @Test
    void refusesAStationOnlyMappingRatherThanSubstitutingTheStationId() {
        LocationId locationId = new LocationId(UUID.randomUUID());
        ProviderLocationMapping stationOnly = ProviderLocationMapping.create(ProviderLocationMappingId.generate(),
                FLIXBUS, locationId, new ProviderPlaceRef(null, "station-id-1", "MGBS"), null, NOW);
        stationOnly.markVerified(NOW);
        repository.save(stationOnly);

        ResolveProviderLocations.Result result = service.resolve(
                new ResolveProviderLocations.Query(FLIXBUS, List.of(locationId)));

        assertThat(result.cityIdsByLocation()).isEmpty();
        assertThat(result.unresolved()).containsExactly(locationId);
    }

    @Test
    void reportsAnUnmappedLocationRatherThanGuessingOne() {
        LocationId neverMapped = new LocationId(UUID.randomUUID());

        ResolveProviderLocations.Result result = service.resolve(
                new ResolveProviderLocations.Query(FLIXBUS, List.of(neverMapped)));

        assertThat(result.unresolved()).containsExactly(neverMapped);
    }

    @Test
    void doesNotLeakOneProvidersCityIdToAnother() {
        LocationId hyderabad = mapped("flixbus-hyderabad", true);

        ResolveProviderLocations.Result result = service.resolve(
                new ResolveProviderLocations.Query(new ProviderCode("OTHERBUS"), List.of(hyderabad)));

        assertThat(result.cityIdsByLocation()).isEmpty();
        assertThat(result.unresolved()).containsExactly(hyderabad);
    }

    @Test
    void resolvesTheTranslatableHalfOfAMixedRequest() {
        LocationId mapped = mapped("flixbus-bengaluru", true);
        LocationId unmapped = new LocationId(UUID.randomUUID());

        ResolveProviderLocations.Result result = service.resolve(
                new ResolveProviderLocations.Query(FLIXBUS, List.of(mapped, unmapped)));

        assertThat(result.cityIdsByLocation()).containsOnlyKeys(mapped);
        assertThat(result.unresolved()).containsExactly(unmapped);
    }

    @Test
    void rejectsAnEmptyRequest() {
        assertThatThrownBy(() -> new ResolveProviderLocations.Query(FLIXBUS, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
