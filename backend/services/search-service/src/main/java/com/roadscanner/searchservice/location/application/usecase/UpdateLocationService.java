package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.exception.DuplicateGooglePlaceIdException;
import com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.port.in.UpdateLocation;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;

import java.time.Clock;

/**
 * Implements {@link UpdateLocation}.
 *
 * <p>The uniqueness pre-check has to exclude the location being edited — otherwise re-submitting
 * an unchanged form would collide with the row's own Google place id and reject a valid no-op
 * edit.
 */
public class UpdateLocationService implements UpdateLocation {

    private final LocationRepository repository;
    private final Clock clock;

    public UpdateLocationService(LocationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public UpdateLocationResult update(UpdateLocationCommand command) {
        Location location = repository.findById(command.locationId())
                .orElseThrow(() -> new LocationNotFoundException(command.locationId()));

        GooglePlaceId googlePlaceId = command.googlePlaceId();
        if (googlePlaceId != null) {
            repository.findByGooglePlaceId(googlePlaceId)
                    .filter(owner -> !owner.id().equals(location.id()))
                    .ifPresent(owner -> {
                        throw new DuplicateGooglePlaceIdException(googlePlaceId);
                    });
        }

        location.update(
                command.displayName(),
                command.address(),
                command.coordinates(),
                googlePlaceId,
                command.timezone(),
                clock.instant());

        return new UpdateLocationResult(repository.save(location));
    }
}
