package com.roadscanner.inventoryservice.domain.port.out;

import com.roadscanner.inventoryservice.domain.model.City;
import com.roadscanner.inventoryservice.domain.model.CityId;

import java.util.List;
import java.util.Optional;

/** Persistence port for {@link City}. Read-only from this service's own use cases — cities are
 * managed administratively (Flyway seed data today), never created through this service's API
 * (docs/services/inventory-service/domain-model.md). */
public interface CityRepository {

    Optional<City> findById(CityId id);

    List<City> searchByPrefix(String prefix, int limit);
}
