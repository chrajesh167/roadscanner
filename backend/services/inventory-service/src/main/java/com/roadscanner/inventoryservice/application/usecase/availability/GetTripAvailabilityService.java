package com.roadscanner.inventoryservice.application.usecase.availability;

import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.port.in.GetTripAvailability;
import com.roadscanner.inventoryservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.inventoryservice.domain.port.out.ProviderMappingRepository;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Implements {@link GetTripAvailability} — the facade behind {@code search-service}'s frozen
 * availability contract. Never throws: a missing mapping or an unreachable provider both degrade
 * to {@link GetTripAvailability.Result#unknown()}, matching
 * docs/services/inventory-service/boundaries.md's failure-mode section exactly.
 */
public class GetTripAvailabilityService implements GetTripAvailability {

    private final ProviderMappingRepository providerMappingRepository;
    private final ProviderIntegrationClient providerIntegrationClient;

    public GetTripAvailabilityService(ProviderMappingRepository providerMappingRepository,
                                       ProviderIntegrationClient providerIntegrationClient) {
        this.providerMappingRepository = providerMappingRepository;
        this.providerIntegrationClient = providerIntegrationClient;
    }

    @Override
    public Result get(Command command) {
        Optional<ProviderMapping> mapping = providerMappingRepository.findByTripId(command.tripId());
        if (mapping.isEmpty()) {
            return Result.unknown();
        }
        OptionalInt seats = providerIntegrationClient.getAvailableSeatCount(
                mapping.get().providerType(), mapping.get().providerTripId());
        return seats.isPresent() ? Result.known(seats.getAsInt()) : Result.unknown();
    }
}
