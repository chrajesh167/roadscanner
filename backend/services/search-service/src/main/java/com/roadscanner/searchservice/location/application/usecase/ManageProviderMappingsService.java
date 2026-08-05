package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.exception.DuplicateProviderMappingException;
import com.roadscanner.searchservice.location.domain.exception.DuplicateProviderMappingException.Conflict;
import com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException;
import com.roadscanner.searchservice.location.domain.exception.ProviderMappingNotFoundException;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.model.ProviderPlaceRef;
import com.roadscanner.searchservice.location.domain.port.in.ManageProviderMappings;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;

import java.time.Clock;
import java.util.Optional;

/**
 * Implements {@link ManageProviderMappings}.
 *
 * <p>Holds the three uniqueness rules. Each is checked before writing so the caller gets a 409
 * naming the offending field rather than a raw constraint violation surfacing as a 500 — the same
 * division of labour {@code CreateLocationService} uses for Google place ids. V5's unique indexes
 * remain the actual guarantee; two simultaneous requests can both pass these checks, and the
 * database is what stops the second one landing.
 *
 * <p>Every check excludes the row being edited. Without that, updating a mapping without touching
 * its provider ids would find itself and refuse.
 */
public class ManageProviderMappingsService implements ManageProviderMappings {

    private final LocationRepository locationRepository;
    private final ProviderLocationMappingRepository mappingRepository;
    private final Clock clock;

    public ManageProviderMappingsService(LocationRepository locationRepository,
                                         ProviderLocationMappingRepository mappingRepository, Clock clock) {
        this.locationRepository = locationRepository;
        this.mappingRepository = mappingRepository;
        this.clock = clock;
    }

    @Override
    public Optional<ProviderLocationMapping> getById(ProviderLocationMappingId id) {
        return mappingRepository.findById(id);
    }

    @Override
    public ProviderLocationMapping create(CreateCommand command) {
        // The canonical location must already exist. A mapping translates a place RoadScanner has
        // authored; it never brings one into being.
        Location location = locationRepository.findById(command.locationId())
                .orElseThrow(() -> new LocationNotFoundException(command.locationId()));

        requireNotAlreadyMapped(location.id(), command.provider(), null);
        requireProviderIdsUnused(command.provider(), command.placeRef(), null);

        ProviderLocationMapping mapping = ProviderLocationMapping.create(
                ProviderLocationMappingId.generate(), command.provider(), location.id(), command.placeRef(),
                command.metadataJson(), clock.instant());

        // create() always starts unverified — a mapping is a claim until something confirms it.
        // An administrator entering a known-good mapping is that confirmation, so honour the flag
        // through the domain operation rather than by constructing a pre-verified object.
        if (command.verified()) {
            mapping.markVerified(clock.instant());
        }

        return mappingRepository.save(mapping);
    }

    @Override
    public ProviderLocationMapping update(UpdateCommand command) {
        ProviderLocationMapping existing = mappingRepository.findById(command.id())
                .orElseThrow(() -> new ProviderMappingNotFoundException(command.id()));

        requireProviderIdsUnused(existing.provider(), command.placeRef(), existing.id());

        // recordSync is the domain's own "the provider's identifiers now look like this" operation,
        // and it already clears `verified` when they actually changed — a previous human
        // confirmation applied to the old identifiers. Reusing it keeps that rule in one place
        // instead of restating it here.
        existing.recordSync(command.placeRef(), command.metadataJson(), clock.instant());

        if (command.verified()) {
            existing.markVerified(clock.instant());
        }

        return mappingRepository.save(existing);
    }

    @Override
    public void delete(ProviderLocationMappingId id) {
        mappingRepository.deleteById(id);
    }

    /** Rule 1: one canonical location plus one provider yields at most one mapping. */
    private void requireNotAlreadyMapped(LocationId locationId, ProviderCode provider,
                                         ProviderLocationMappingId excluding) {
        mappingRepository.findByLocationAndProvider(locationId, provider)
                .filter(found -> !found.id().equals(excluding))
                .ifPresent(found -> {
                    throw new DuplicateProviderMappingException(Conflict.LOCATION_ALREADY_MAPPED, provider,
                            locationId.toString());
                });
    }

    /** Rules 2 and 3: a provider city id and a provider station id each belong to exactly one
     * canonical location. Both are optional on a mapping, so each is checked only when present. */
    private void requireProviderIdsUnused(ProviderCode provider, ProviderPlaceRef placeRef,
                                          ProviderLocationMappingId excluding) {
        if (placeRef.cityId() != null) {
            mappingRepository.findByProviderCityId(provider, placeRef.cityId())
                    .filter(found -> !found.id().equals(excluding))
                    .ifPresent(found -> {
                        throw new DuplicateProviderMappingException(Conflict.PROVIDER_CITY_ID_IN_USE, provider,
                                placeRef.cityId());
                    });
        }

        if (placeRef.stationId() != null) {
            mappingRepository.findByProviderStationId(provider, placeRef.stationId())
                    .filter(found -> !found.id().equals(excluding))
                    .ifPresent(found -> {
                        throw new DuplicateProviderMappingException(Conflict.PROVIDER_STATION_ID_IN_USE, provider,
                                placeRef.stationId());
                    });
        }
    }
}
