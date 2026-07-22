package com.roadscanner.inventoryservice.application.usecase.catalog;

import com.roadscanner.inventoryservice.domain.exception.ProviderMappingNotFoundException;
import com.roadscanner.inventoryservice.domain.port.in.GetProviderMapping;
import com.roadscanner.inventoryservice.domain.port.out.ProviderMappingRepository;

/** Implements {@link GetProviderMapping}. */
public class GetProviderMappingService implements GetProviderMapping {

    private final ProviderMappingRepository providerMappingRepository;

    public GetProviderMappingService(ProviderMappingRepository providerMappingRepository) {
        this.providerMappingRepository = providerMappingRepository;
    }

    @Override
    public Result get(Command command) {
        return new Result(providerMappingRepository.findByTripId(command.tripId())
                .orElseThrow(() -> new ProviderMappingNotFoundException(command.tripId())));
    }
}
