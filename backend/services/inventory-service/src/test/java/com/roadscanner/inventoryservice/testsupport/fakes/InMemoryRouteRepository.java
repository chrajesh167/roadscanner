package com.roadscanner.inventoryservice.testsupport.fakes;

import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.model.Route;
import com.roadscanner.inventoryservice.domain.model.RouteId;
import com.roadscanner.inventoryservice.domain.port.out.RouteRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryRouteRepository implements RouteRepository {

    private final Map<RouteId, Route> routes = new LinkedHashMap<>();

    public void add(Route route) {
        routes.put(route.id(), route);
    }

    @Override
    public Optional<Route> findById(RouteId id) {
        return Optional.ofNullable(routes.get(id));
    }

    @Override
    public Optional<Route> findByCities(CityId originCityId, CityId destinationCityId) {
        return routes.values().stream()
                .filter(r -> r.originCityId().equals(originCityId) && r.destinationCityId().equals(destinationCityId))
                .findFirst();
    }

    @Override
    public List<Route> findAll() {
        return List.copyOf(routes.values());
    }
}
