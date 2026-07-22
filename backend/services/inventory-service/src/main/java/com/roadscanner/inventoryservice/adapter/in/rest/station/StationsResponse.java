package com.roadscanner.inventoryservice.adapter.in.rest.station;

import com.roadscanner.inventoryservice.domain.port.in.BrowseStations;

import java.util.List;

public record StationsResponse(List<StationResponse> stations) {

    public static StationsResponse from(BrowseStations.Result result) {
        return new StationsResponse(result.stations().stream().map(StationResponse::from).toList());
    }
}
