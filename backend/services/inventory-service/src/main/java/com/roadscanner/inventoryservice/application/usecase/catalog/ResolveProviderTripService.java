package com.roadscanner.inventoryservice.application.usecase.catalog;

import com.roadscanner.inventoryservice.domain.exception.ProviderTripNotMappedException;
import com.roadscanner.inventoryservice.domain.port.in.ResolveProviderTrip;
import com.roadscanner.inventoryservice.domain.port.out.ProviderMappingRepository;

/** Implements {@link ResolveProviderTrip} over the repository's existing reverse lookup. */
public class ResolveProviderTripService implements ResolveProviderTrip {

    private final ProviderMappingRepository providerMappingRepository;

    public ResolveProviderTripService(ProviderMappingRepository providerMappingRepository) {
        this.providerMappingRepository = providerMappingRepository;
    }

    @Override
    public Result resolve(Command command) {
        return new Result(providerMappingRepository
                .findByProviderTypeAndProviderTripId(command.providerType(), command.providerTripId())
                .orElseThrow(() -> new ProviderTripNotMappedException(
                        command.providerType(), command.providerTripId())));
    }
}
