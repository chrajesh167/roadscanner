package com.roadscanner.inventoryservice.adapter.in.rest.city;

import com.roadscanner.inventoryservice.domain.port.in.BrowseCities;

import java.util.List;

public record CitiesResponse(List<CityResponse> cities) {

    public static CitiesResponse from(BrowseCities.Result result) {
        return new CitiesResponse(result.cities().stream().map(CityResponse::from).toList());
    }
}
