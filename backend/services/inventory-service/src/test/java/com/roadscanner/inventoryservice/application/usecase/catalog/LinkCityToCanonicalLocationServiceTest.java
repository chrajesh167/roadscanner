package com.roadscanner.inventoryservice.application.usecase.catalog;

import com.roadscanner.inventoryservice.domain.exception.CityNotFoundException;
import com.roadscanner.inventoryservice.domain.model.City;
import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.port.in.LinkCityToCanonicalLocation;
import com.roadscanner.inventoryservice.testsupport.fakes.InMemoryCityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The administrative act {@code V3__link_cities_to_canonical_locations.sql} describes but had no
 * mechanism for.
 *
 * <p>Canonical location ids are minted per environment, so no migration can carry a correct
 * literal and no automatic derivation is permitted — matching city names is the mistranslation this
 * platform refuses by name. Someone states the pairing; these tests pin what happens when they do,
 * and what happens when they state a second, conflicting one.
 */
class LinkCityToCanonicalLocationServiceTest {

    private static final CityId HYDERABAD = new CityId(UUID.fromString("11111111-1111-1111-1111-111111111106"));

    private InMemoryCityRepository cities;
    private LinkCityToCanonicalLocation linkCity;

    @BeforeEach
    void setUp() {
        cities = new InMemoryCityRepository();
        cities.add(City.create(HYDERABAD, "Hyderabad", "Telangana", "India"));
        linkCity = new LinkCityToCanonicalLocationService(cities);
    }

    @Test
    void recordsTheCanonicalLocationSoCatalogSyncCanTranslateTheCity() {
        UUID canonical = UUID.randomUUID();

        LinkCityToCanonicalLocation.Result result =
                linkCity.link(new LinkCityToCanonicalLocation.Command(HYDERABAD, canonical));

        assertThat(result.city().locationId()).contains(canonical);
        // Persisted, not just returned: sync reads the city back on its next pass.
        assertThat(cities.findById(HYDERABAD).orElseThrow().locationId()).contains(canonical);
    }

    @Test
    void relinkingToTheSameLocationIsAcceptedSoASetupScriptCanBeRerun() {
        UUID canonical = UUID.randomUUID();
        linkCity.link(new LinkCityToCanonicalLocation.Command(HYDERABAD, canonical));

        LinkCityToCanonicalLocation.Result result =
                linkCity.link(new LinkCityToCanonicalLocation.Command(HYDERABAD, canonical));

        assertThat(result.city().locationId()).contains(canonical);
    }

    @Test
    void refusesToRepointAnAlreadyLinkedCityAtADifferentLocation() {
        UUID original = UUID.randomUUID();
        UUID different = UUID.randomUUID();
        linkCity.link(new LinkCityToCanonicalLocation.Command(HYDERABAD, original));

        // Trips already synchronised resolved their provider city ids through the original
        // location. Repointing silently would leave every one of them mapped to a city this row no
        // longer claims to be — corruption that only surfaces later, as a booking against the wrong
        // city.
        assertThatThrownBy(() -> linkCity.link(new LinkCityToCanonicalLocation.Command(HYDERABAD, different)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(original.toString());

        assertThat(cities.findById(HYDERABAD).orElseThrow().locationId()).contains(original);
    }

    @Test
    void anUnknownCityIsReportedRatherThanCreated() {
        CityId unknown = new CityId(UUID.randomUUID());

        assertThatThrownBy(() -> linkCity.link(new LinkCityToCanonicalLocation.Command(unknown, UUID.randomUUID())))
                .isInstanceOf(CityNotFoundException.class);
    }
}
