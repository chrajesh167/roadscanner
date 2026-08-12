package com.roadscanner.inventoryservice.adapter.in.rest.city;

import com.roadscanner.inventoryservice.domain.model.City;

/** Confirms the recorded link, echoing the city so an operator can see which one they just bound. */
public record CanonicalLocationLinkResponse(String cityId, String name, String canonicalLocationId) {

    public static CanonicalLocationLinkResponse from(City city) {
        return new CanonicalLocationLinkResponse(city.id().toString(), city.name(),
                city.locationId().map(Object::toString).orElse(null));
    }
}
