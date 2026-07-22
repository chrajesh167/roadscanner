package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.model.Route;
import com.roadscanner.inventoryservice.domain.model.RouteId;

final class RouteMapper {

    Route toDomain(RouteJpaEntity entity) {
        return Route.reconstitute(new RouteId(entity.getId()), new CityId(entity.getOriginCityId()),
                new CityId(entity.getDestinationCityId()), entity.getDistanceKm());
    }
}
