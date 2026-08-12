package com.roadscanner.inventoryservice.domain.port.out;

import com.roadscanner.inventoryservice.domain.model.City;
import com.roadscanner.inventoryservice.domain.model.CityId;

import java.util.List;
import java.util.Optional;

/** Persistence port for {@link City}. Cities are managed administratively (Flyway seed data
 * today) and are never created through this service's API
 * (docs/services/inventory-service/domain-model.md); {@link #save} exists only to record the
 * canonical-location link, which cannot be seeded because canonical ids are minted per
 * environment — see {@code V3__link_cities_to_canonical_locations.sql}. */
public interface CityRepository {

    Optional<City> findById(CityId id);

    List<City> searchByPrefix(String prefix, int limit);

    /** Persists an existing city. Creation stays out of scope: every city already exists by seed. */
    City save(City city);
}
