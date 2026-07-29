package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.exception.DuplicateGooglePlaceIdException;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.port.in.CreateLocation;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;

import java.time.Clock;
import java.time.Instant;

/**
 * Implements {@link CreateLocation}.
 *
 * <p>Takes a {@link Clock} rather than calling {@code Instant.now()} directly so timestamps are
 * assertable in tests without freezing global state — the same reason the domain's mutators all
 * accept an explicit {@code now}.
 */
public class CreateLocationService implements CreateLocation {

    private final LocationRepository repository;
    private final Clock clock;

    public CreateLocationService(LocationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public CreateLocationResult create(CreateLocationCommand command) {
        GooglePlaceId googlePlaceId = command.googlePlaceId();
        if (googlePlaceId != null) {
            // Pre-checked purely so the caller gets a precise 409 instead of a raw constraint
            // violation. The unique index is still the real guarantee under concurrency.
            repository.findByGooglePlaceId(googlePlaceId).ifPresent(existing -> {
                throw new DuplicateGooglePlaceIdException(googlePlaceId);
            });
        }

        Instant now = clock.instant();
        Location location = Location.create(
                LocationId.generate(),
                command.displayName(),
                command.address(),
                command.coordinates(),
                googlePlaceId,
                command.timezone(),
                now);

        return new CreateLocationResult(repository.save(location));
    }
}
