package com.roadscanner.inventoryservice.adapter.in.rest.city;

import com.roadscanner.inventoryservice.domain.model.City;

public record CityResponse(String id, String name, String state, String country) {

    public static CityResponse from(City city) {
        return new CityResponse(city.id().toString(), city.name(), city.state(), city.country());
    }
}
