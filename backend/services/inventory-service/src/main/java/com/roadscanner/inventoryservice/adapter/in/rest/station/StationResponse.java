package com.roadscanner.inventoryservice.adapter.in.rest.station;

import com.roadscanner.inventoryservice.domain.model.Station;

public record StationResponse(String id, String cityId, String name, String type, Double latitude, Double longitude) {

    public static StationResponse from(Station station) {
        return new StationResponse(station.id().toString(), station.cityId().toString(), station.name(),
                station.type().name(), station.latitude().orElse(null), station.longitude().orElse(null));
    }
}
