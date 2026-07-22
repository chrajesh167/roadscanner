package com.roadscanner.inventoryservice.domain.port.out;

import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.model.Route;
import com.roadscanner.inventoryservice.domain.model.RouteId;

import java.util.List;
import java.util.Optional;

/** Persistence port for {@link Route}. Read-only from this service's own use cases, same
 * administrative-management posture as {@link CityRepository}. {@link #findAll()} backs catalog
 * synchronization — it searches every known route against each provider. */
public interface RouteRepository {

    Optional<Route> findById(RouteId id);

    Optional<Route> findByCities(CityId originCityId, CityId destinationCityId);

    List<Route> findAll();
}
