package com.roadscanner.searchservice.application.usecase.detail;

import com.roadscanner.searchservice.application.usecase.availability.AvailabilityOverlay;
import com.roadscanner.searchservice.domain.exception.TripNotFoundException;
import com.roadscanner.searchservice.domain.model.SearchableTrip;
import com.roadscanner.searchservice.domain.port.in.GetTripDetail;
import com.roadscanner.searchservice.domain.port.out.SearchableTripRepository;

/**
 * Implements {@link GetTripDetail} — the same trip-plus-availability composition
 * {@code SearchTripsService} performs per result row, applied to a single trip
 * (docs/services/search-service/use-cases.md: "Live availability lookup is not modeled as its
 * own use case").
 */
public class GetTripDetailService implements GetTripDetail {

    private final SearchableTripRepository repository;
    private final AvailabilityOverlay availabilityOverlay;

    public GetTripDetailService(SearchableTripRepository repository, AvailabilityOverlay availabilityOverlay) {
        this.repository = repository;
        this.availabilityOverlay = availabilityOverlay;
    }

    @Override
    public GetTripDetailResult getDetail(GetTripDetailCommand command) {
        SearchableTrip trip = repository.findByTripId(command.tripId())
                .orElseThrow(() -> new TripNotFoundException(command.tripId()));
        return new GetTripDetailResult(availabilityOverlay.overlay(trip));
    }
}
