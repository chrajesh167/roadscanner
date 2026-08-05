package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.exception.DuplicateProviderMappingException;
import com.roadscanner.searchservice.location.domain.exception.DuplicateProviderMappingException.Conflict;
import com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException;
import com.roadscanner.searchservice.location.domain.exception.ProviderMappingNotFoundException;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.model.ProviderPlaceRef;
import com.roadscanner.searchservice.location.domain.port.in.ManageProviderMappings;
import com.roadscanner.searchservice.location.testsupport.InMemoryLocationRepository;
import com.roadscanner.searchservice.location.testsupport.InMemoryProviderLocationMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The three uniqueness rules, and the immutability of what a mapping translates.
 *
 * <p>These checks are the reason an administrator gets a 409 naming a field instead of a 500 from
 * a constraint violation. V5's unique indexes are the actual guarantee under concurrency — what is
 * asserted here is the error message and the fact that the write is refused before it reaches the
 * database.
 */
class ManageProviderMappingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
    private static final ProviderCode FLIXBUS = new ProviderCode("FLIXBUS");
    private static final ProviderCode REDBUS = new ProviderCode("REDBUS");

    private InMemoryLocationRepository locations;
    private InMemoryProviderLocationMappingRepository mappings;
    private ManageProviderMappings service;

    private Location hyderabad;
    private Location bengaluru;

    @BeforeEach
    void setUp() {
        locations = new InMemoryLocationRepository();
        mappings = new InMemoryProviderLocationMappingRepository();
        service = new ManageProviderMappingsService(locations, mappings,
                Clock.fixed(NOW, ZoneOffset.UTC));

        hyderabad = location("Hyderabad", "Hyderabad");
        bengaluru = location("Bengaluru", "Bengaluru");
        locations.seed(hyderabad, bengaluru);
    }

    private static Location location(String displayName, String city) {
        return Location.create(LocationId.generate(), displayName,
                new LocationAddress(city, "Telangana", "India"), null, null, null, NOW);
    }

    private ManageProviderMappings.CreateCommand createCommand(Location location, ProviderCode provider,
                                                               String cityId, String stationId) {
        return new ManageProviderMappings.CreateCommand(location.id(), provider,
                new ProviderPlaceRef(cityId, stationId, null), null, false);
    }

    // ---------- Create ----------

    @Test
    void createsAMappingAgainstAnExistingCanonicalLocation() {
        ProviderLocationMapping created = service.create(createCommand(hyderabad, FLIXBUS, "city-1", null));

        assertThat(created.locationId()).isEqualTo(hyderabad.id());
        assertThat(created.provider()).isEqualTo(FLIXBUS);
        assertThat(created.placeRef().cityId()).isEqualTo("city-1");
        assertThat(created.isVerified()).isFalse();
    }

    @Test
    void refusesAMappingForALocationThatDoesNotExist() {
        LocationId unknown = LocationId.generate();

        assertThatThrownBy(() -> service.create(new ManageProviderMappings.CreateCommand(
                unknown, FLIXBUS, new ProviderPlaceRef("city-1", null, null), null, false)))
                .isInstanceOf(LocationNotFoundException.class);

        // The mapping module never authors canonical locations — a missing one is refused, not
        // created on the caller's behalf.
        assertThat(locations.count()).isEqualTo(2);
    }

    @Test
    void honoursTheVerifiedFlagWhenAnAdministratorSuppliesAKnownGoodMapping() {
        ProviderLocationMapping created = service.create(new ManageProviderMappings.CreateCommand(
                hyderabad.id(), FLIXBUS, new ProviderPlaceRef("city-1", null, null), null, true));

        assertThat(created.isVerified()).isTrue();
    }

    // ---------- Rule 1: one location + one provider ----------

    @Test
    void refusesASecondMappingForTheSameLocationAndProvider() {
        service.create(createCommand(hyderabad, FLIXBUS, "city-1", null));

        assertThatThrownBy(() -> service.create(createCommand(hyderabad, FLIXBUS, "city-2", null)))
                .isInstanceOf(DuplicateProviderMappingException.class)
                .extracting(e -> ((DuplicateProviderMappingException) e).conflict())
                .isEqualTo(Conflict.LOCATION_ALREADY_MAPPED);
    }

    @Test
    void allowsTheSameLocationToBeMappedByDifferentProviders() {
        service.create(createCommand(hyderabad, FLIXBUS, "flix-city", null));
        ProviderLocationMapping second = service.create(createCommand(hyderabad, REDBUS, "red-city", null));

        // The whole point of the translation layer: one canonical place, many provider vocabularies.
        assertThat(second.locationId()).isEqualTo(hyderabad.id());
        assertThat(mappings.findByLocation(hyderabad.id())).hasSize(2);
    }

    // ---------- Rule 2: one provider city id ----------

    @Test
    void refusesAProviderCityIdAlreadyMappedToAnotherLocation() {
        service.create(createCommand(hyderabad, FLIXBUS, "city-1", null));

        assertThatThrownBy(() -> service.create(createCommand(bengaluru, FLIXBUS, "city-1", null)))
                .isInstanceOf(DuplicateProviderMappingException.class)
                .extracting(e -> ((DuplicateProviderMappingException) e).conflict())
                .isEqualTo(Conflict.PROVIDER_CITY_ID_IN_USE);
    }

    @Test
    void allowsTwoProvidersToUseTheSameCityIdString() {
        service.create(createCommand(hyderabad, FLIXBUS, "42", null));

        // Provider ids are opaque and namespaced by provider — FlixBus's "42" and RedBus's "42"
        // are unrelated values that happen to look alike.
        ProviderLocationMapping other = service.create(createCommand(bengaluru, REDBUS, "42", null));
        assertThat(other.placeRef().cityId()).isEqualTo("42");
    }

    // ---------- Rule 3: one provider station id ----------

    @Test
    void refusesAProviderStationIdAlreadyMappedToAnotherLocation() {
        service.create(createCommand(hyderabad, FLIXBUS, null, "station-1"));

        assertThatThrownBy(() -> service.create(createCommand(bengaluru, FLIXBUS, null, "station-1")))
                .isInstanceOf(DuplicateProviderMappingException.class)
                .extracting(e -> ((DuplicateProviderMappingException) e).conflict())
                .isEqualTo(Conflict.PROVIDER_STATION_ID_IN_USE);
    }

    @Test
    void doesNotTreatTwoStationLessMappingsAsColliding() {
        service.create(createCommand(hyderabad, FLIXBUS, "city-1", null));

        // Both carry a null station id. Absence is not a value, so it cannot collide — this is the
        // rule Postgres gives for free by treating NULLs as distinct in a unique index, and the
        // pre-check has to agree with it or it would refuse writes the database would accept.
        ProviderLocationMapping second = service.create(createCommand(bengaluru, FLIXBUS, "city-2", null));
        assertThat(second.placeRef().stationId()).isNull();
    }

    // ---------- Update ----------

    @Test
    void updatesTheEditableFieldsWithoutTouchingWhatTheMappingTranslates() {
        ProviderLocationMapping created = service.create(createCommand(hyderabad, FLIXBUS, "city-1", null));

        ProviderLocationMapping updated = service.update(new ManageProviderMappings.UpdateCommand(
                created.id(), new ProviderPlaceRef("city-1", "station-9", "MGBS"), null, true));

        assertThat(updated.placeRef().stationId()).isEqualTo("station-9");
        assertThat(updated.placeRef().stationName()).isEqualTo("MGBS");
        assertThat(updated.isVerified()).isTrue();
        // Immutable: an update can never re-point a mapping at a different place or provider.
        assertThat(updated.locationId()).isEqualTo(hyderabad.id());
        assertThat(updated.provider()).isEqualTo(FLIXBUS);
    }

    @Test
    void anUpdateThatChangesNoProviderIdDoesNotCollideWithItself() {
        ProviderLocationMapping created = service.create(createCommand(hyderabad, FLIXBUS, "city-1", null));

        // Without excluding the row being edited, every no-op edit would find its own row and be
        // refused as a duplicate.
        ProviderLocationMapping updated = service.update(new ManageProviderMappings.UpdateCommand(
                created.id(), new ProviderPlaceRef("city-1", null, "Renamed"), null, false));

        assertThat(updated.placeRef().stationName()).isEqualTo("Renamed");
    }

    @Test
    void refusesAnUpdateThatWouldStealAnotherMappingsProviderCityId() {
        service.create(createCommand(hyderabad, FLIXBUS, "city-1", null));
        ProviderLocationMapping second = service.create(createCommand(bengaluru, FLIXBUS, "city-2", null));

        assertThatThrownBy(() -> service.update(new ManageProviderMappings.UpdateCommand(
                second.id(), new ProviderPlaceRef("city-1", null, null), null, false)))
                .isInstanceOf(DuplicateProviderMappingException.class)
                .extracting(e -> ((DuplicateProviderMappingException) e).conflict())
                .isEqualTo(Conflict.PROVIDER_CITY_ID_IN_USE);
    }

    @Test
    void clearsVerifiedWhenTheProviderIdentifiersActuallyChanged() {
        ProviderLocationMapping created = service.create(new ManageProviderMappings.CreateCommand(
                hyderabad.id(), FLIXBUS, new ProviderPlaceRef("city-1", null, null), null, true));
        assertThat(created.isVerified()).isTrue();

        ProviderLocationMapping updated = service.update(new ManageProviderMappings.UpdateCommand(
                created.id(), new ProviderPlaceRef("city-CHANGED", null, null), null, false));

        // A previous confirmation applied to the old identifiers and cannot carry over — the
        // domain's own recordSync rule, reached through the update path.
        assertThat(updated.isVerified()).isFalse();
    }

    @Test
    void refusesToUpdateAMappingThatDoesNotExist() {
        assertThatThrownBy(() -> service.update(new ManageProviderMappings.UpdateCommand(
                ProviderLocationMappingId.generate(), new ProviderPlaceRef("city-1", null, null), null, false)))
                .isInstanceOf(ProviderMappingNotFoundException.class);
    }

    // ---------- Delete ----------

    @Test
    void deletesAMapping() {
        ProviderLocationMapping created = service.create(createCommand(hyderabad, FLIXBUS, "city-1", null));

        service.delete(created.id());

        assertThat(mappings.findById(created.id())).isEmpty();
    }

    @Test
    void deleteIsIdempotent() {
        ProviderLocationMapping created = service.create(createCommand(hyderabad, FLIXBUS, "city-1", null));
        service.delete(created.id());

        // The caller's intent — that this translation should not exist — is already satisfied.
        service.delete(created.id());

        assertThat(mappings.findById(created.id())).isEmpty();
    }

    @Test
    void deletingAMappingFreesItsProviderIdsForReuse() {
        ProviderLocationMapping created = service.create(createCommand(hyderabad, FLIXBUS, "city-1", null));
        service.delete(created.id());

        // A hard delete rather than a soft one, so the unique keys are genuinely released — a
        // mapping corrected by delete-then-create must not collide with the row it replaced.
        ProviderLocationMapping recreated = service.create(createCommand(bengaluru, FLIXBUS, "city-1", null));
        assertThat(recreated.placeRef().cityId()).isEqualTo("city-1");
    }
}
