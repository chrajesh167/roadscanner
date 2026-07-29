package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException;
import com.roadscanner.searchservice.location.domain.port.in.GetProviderMapping;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.port.out.LocationRepository;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;

/**
 * Implements {@link GetProviderMapping}.
 *
 * <p>Verifies the location exists before answering. Without that check an unknown id and a known
 * id with no mapping would both return empty, which are different problems: the first is a bad
 * request, the second is normal. Sprint 3's callers need to tell them apart to decide whether to
 * fail the request or fall back to another provider.
 */
public class GetProviderMappingService implements GetProviderMapping {

    private final LocationRepository locationRepository;
    private final ProviderLocationMappingRepository mappingRepository;

    public GetProviderMappingService(LocationRepository locationRepository,
                                     ProviderLocationMappingRepository mappingRepository) {
        this.locationRepository = locationRepository;
        this.mappingRepository = mappingRepository;
    }

    @Override
    public GetProviderMappingResult get(GetProviderMappingCommand command) {
        requireLocationExists(command.locationId());
        return new GetProviderMappingResult(
                mappingRepository.findByLocationAndProvider(command.locationId(), command.provider()));
    }

    @Override
    public ProviderMappingsResult getAll(LocationId locationId) {
        requireLocationExists(locationId);
        return new ProviderMappingsResult(mappingRepository.findByLocation(locationId));
    }

    private void requireLocationExists(LocationId locationId) {
        if (locationRepository.findById(locationId).isEmpty()) {
            throw new LocationNotFoundException(locationId);
        }
    }
}
