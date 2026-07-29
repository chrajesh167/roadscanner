package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.port.in.DisableLocation;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;

import java.time.Clock;

/**
 * Implements {@link DisableLocation}.
 *
 * <p>Idempotent end to end: the aggregate reports whether it actually changed, and this service
 * skips the write when it did not. A retried DELETE therefore neither errors nor pointlessly
 * bumps {@code updated_at}.
 */
public class DisableLocationService implements DisableLocation {

    private final LocationRepository repository;
    private final Clock clock;

    public DisableLocationService(LocationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public DisableLocationResult disable(DisableLocationCommand command) {
        Location location = repository.findById(command.locationId())
                .orElseThrow(() -> new LocationNotFoundException(command.locationId()));

        boolean changed = location.disable(clock.instant());
        if (changed) {
            repository.save(location);
        }

        return new DisableLocationResult(location.id(), !changed);
    }
}
