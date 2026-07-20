package com.roadscanner.providerintegrationservice.adapter.in.rest.search;

import com.roadscanner.providerintegrationservice.domain.port.in.SearchTrips;

import java.util.List;

public record SearchTripsResponse(List<ProviderTripResponse> trips) {

    public static SearchTripsResponse from(SearchTrips.Result result) {
        return new SearchTripsResponse(result.trips().stream().map(ProviderTripResponse::from).toList());
    }
}
