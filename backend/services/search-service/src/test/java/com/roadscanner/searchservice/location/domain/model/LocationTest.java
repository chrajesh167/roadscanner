package com.roadscanner.searchservice.location.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The {@link Location} aggregate's own invariants — no Spring, no database. */
class LocationTest {

    private static final Instant CREATED = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-07-02T00:00:00Z");
    private static final LocationAddress HYDERABAD = new LocationAddress("Hyderabad", "Telangana", "India");

    private static Location hyderabad() {
        return Location.create(LocationId.generate(), "Hyderabad", HYDERABAD, null, null, "Asia/Kolkata", CREATED);
    }

    @Test
    void isActiveWhenCreated() {
        Location location = hyderabad();

        assertThat(location.isActive()).isTrue();
        assertThat(location.createdAt()).isEqualTo(CREATED);
        assertThat(location.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void rejectsABlankDisplayName() {
        assertThatThrownBy(() -> Location.create(LocationId.generate(), "   ", HYDERABAD, null, null, null, CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName");
    }

    @Test
    void rejectsAnOverlongDisplayName() {
        String tooLong = "x".repeat(256);

        assertThatThrownBy(() -> Location.create(LocationId.generate(), tooLong, HYDERABAD, null, null, null, CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }

    @Test
    void trimsTheDisplayName() {
        Location location = Location.create(LocationId.generate(), "  Hyderabad  ", HYDERABAD, null, null, null,
                CREATED);

        assertThat(location.displayName()).isEqualTo("Hyderabad");
    }

    @Test
    void updateReplacesEveryMutableFieldAndClearsOmittedOptionals() {
        Location location = Location.create(LocationId.generate(), "Hyderabad", HYDERABAD,
                new GeoCoordinates(new BigDecimal("17.3850"), new BigDecimal("78.4867")),
                new GooglePlaceId("place-1"), "Asia/Kolkata", CREATED);

        location.update("Secunderabad", new LocationAddress("Secunderabad", null, "India"), null, null, null, LATER);

        assertThat(location.displayName()).isEqualTo("Secunderabad");
        assertThat(location.address().stateIfPresent()).isEmpty();
        // A PUT that omits an optional field means "clear it", not "keep what was there".
        assertThat(location.coordinates()).isEmpty();
        assertThat(location.googlePlaceId()).isEmpty();
        assertThat(location.timezone()).isEmpty();
        assertThat(location.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void updateDoesNotChangeActiveState() {
        Location location = hyderabad();
        location.disable(LATER);

        location.update("Hyderabad", HYDERABAD, null, null, null, LATER);

        assertThat(location.isActive()).isFalse();
    }

    @Test
    void disableIsIdempotent() {
        Location location = hyderabad();

        assertThat(location.disable(LATER)).isTrue();
        assertThat(location.isActive()).isFalse();
        assertThat(location.updatedAt()).isEqualTo(LATER);

        Instant evenLater = LATER.plusSeconds(60);
        assertThat(location.disable(evenLater)).isFalse();
        // A no-op must not bump updatedAt — a retried DELETE should leave no trace.
        assertThat(location.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void activateRestoresADisabledLocation() {
        Location location = hyderabad();
        location.disable(LATER);

        assertThat(location.activate(LATER)).isTrue();
        assertThat(location.isActive()).isTrue();
        assertThat(location.activate(LATER)).isFalse();
    }

    @Test
    void attachGooglePlaceIdSetsItWhenAbsent() {
        Location location = hyderabad();

        assertThat(location.attachGooglePlaceId(new GooglePlaceId("place-1"), LATER)).isTrue();
        assertThat(location.googlePlaceId()).contains(new GooglePlaceId("place-1"));
    }

    @Test
    void attachGooglePlaceIdNeverSilentlyRepointsAnExistingAssociation() {
        Location location = Location.create(LocationId.generate(), "Hyderabad", HYDERABAD, null,
                new GooglePlaceId("place-1"), null, CREATED);

        assertThat(location.attachGooglePlaceId(new GooglePlaceId("place-2"), LATER)).isFalse();
        assertThat(location.googlePlaceId()).contains(new GooglePlaceId("place-1"));

        // Re-attaching the same id is a harmless no-op, reported as success.
        assertThat(location.attachGooglePlaceId(new GooglePlaceId("place-1"), LATER)).isTrue();
    }

    @Test
    void identityIsTheIdAlone() {
        LocationId id = LocationId.generate();
        Location one = Location.create(id, "Hyderabad", HYDERABAD, null, null, null, CREATED);
        Location two = Location.create(id, "Completely Different", new LocationAddress("Pune", null, "India"),
                null, null, null, LATER);

        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
    }
}
