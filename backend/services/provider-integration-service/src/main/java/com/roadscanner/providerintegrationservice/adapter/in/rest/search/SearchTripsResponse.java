package com.roadscanner.providerintegrationservice.adapter.in.rest.search;

import com.roadscanner.providerintegrationservice.domain.port.in.SearchTrips;

import java.util.List;

public record SearchTripsResponse(List<ProviderTripResponse> trips) {

    public static SearchTripsResponse from(SearchTrips.Result result) {
        return fromTrips(result.trips());
    }

    /** Shared by both search routes so one wire shape serves session-scoped and session-less callers. */
    public static SearchTripsResponse fromTrips(
            java.util.List<com.roadscanner.providerintegrationservice.domain.model.ProviderTrip> trips) {
        return new SearchTripsResponse(trips.stream().map(ProviderTripResponse::from).toList());
    }
}
