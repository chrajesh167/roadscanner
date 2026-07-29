package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.port.in.GetLocation;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;

/**
 * Implements {@link GetLocation}.
 *
 * <p>Resolves inactive locations as readily as active ones — a soft-deleted place must stay
 * addressable for historical bookings that reference it. Hiding it is the autocomplete path's
 * job, not this one's.
 */
public class GetLocationService implements GetLocation {

    private final LocationRepository repository;

    public GetLocationService(LocationRepository repository) {
        this.repository = repository;
    }

    @Override
    public GetLocationResult get(GetLocationCommand command) {
        Location location = repository.findById(command.locationId())
                .orElseThrow(() -> new LocationNotFoundException(command.locationId()));
        return new GetLocationResult(location);
    }
}
