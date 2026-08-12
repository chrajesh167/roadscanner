package com.roadscanner.inventoryservice.domain.port.in;

import com.roadscanner.inventoryservice.domain.model.City;
import com.roadscanner.inventoryservice.domain.model.CityId;

import java.util.Objects;
import java.util.UUID;

/**
 * Records which canonical RoadScanner location a catalog city is.
 *
 * <p>The administrative act {@code V3__link_cities_to_canonical_locations.sql} describes and
 * deliberately declines to perform: canonical location ids are minted per environment, so no
 * migration can carry a correct literal, and deriving one by matching city names is the
 * mistranslation this platform refuses by name. Someone has to state the pairing for the
 * environment in question, and this is where they state it.
 *
 * <p>Until a city is linked, catalog sync skips every route through it. That stays true — this
 * supplies the missing link, it does not relax the rule.
 *
 * <p>Raises {@link com.roadscanner.inventoryservice.domain.exception.CityNotFoundException} for an
 * unknown city, and {@link IllegalStateException} when the city is already linked elsewhere.
 */
public interface LinkCityToCanonicalLocation {

    Result link(Command command);

    record Command(CityId cityId, UUID canonicalLocationId) {
        public Command {
            Objects.requireNonNull(cityId, "cityId must not be null");
            Objects.requireNonNull(canonicalLocationId, "canonicalLocationId must not be null");
        }
    }

    record Result(City city) {
        public Result {
            Objects.requireNonNull(city, "city must not be null");
        }
    }
}
