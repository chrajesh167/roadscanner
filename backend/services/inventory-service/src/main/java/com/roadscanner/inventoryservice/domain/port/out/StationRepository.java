package com.roadscanner.inventoryservice.domain.port.out;

import com.roadscanner.inventoryservice.domain.model.CityId;
import com.roadscanner.inventoryservice.domain.model.Station;
import com.roadscanner.inventoryservice.domain.model.StationId;

import java.util.List;
import java.util.Optional;

public interface StationRepository {

    Optional<Station> findById(StationId id);

    List<Station> search(String prefix, CityId cityId, int limit);
}
