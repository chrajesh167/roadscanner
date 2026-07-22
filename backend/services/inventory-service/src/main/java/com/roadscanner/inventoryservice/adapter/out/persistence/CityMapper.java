package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.City;
import com.roadscanner.inventoryservice.domain.model.CityId;

final class CityMapper {

    City toDomain(CityJpaEntity entity) {
        return City.reconstitute(new CityId(entity.getId()), entity.getName(), entity.getState(), entity.getCountry());
    }
}
