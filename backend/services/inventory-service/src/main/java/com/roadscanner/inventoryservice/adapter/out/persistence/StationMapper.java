package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.model.Station;
import com.roadscanner.inventoryservice.domain.model.StationId;
import com.roadscanner.inventoryservice.domain.model.StationType;

final class StationMapper {

    Station toDomain(StationJpaEntity entity) {
        return Station.reconstitute(new StationId(entity.getId()), new CityId(entity.getCityId()), entity.getName(),
                StationType.valueOf(entity.getType()), entity.getLatitude(), entity.getLongitude());
    }
}
