package com.roadscanner.inventoryservice.domain.port.in;

import com.roadscanner.inventoryservice.domain.model.ProviderMapping;
import com.roadscanner.inventoryservice.domain.model.ProviderType;

import java.util.Objects;

/**
 * The reverse of {@link GetProviderMapping}: given a provider's own trip id, which catalog trip is
 * that?
 *
 * <p>Search can hand a caller a live provider trip identified only by {@code (providerType,
 * providerTripId)}, while every booking step downstream is keyed by a catalog {@code TripId}. This
 * is the one translation between those vocabularies, and it resolves rather than invents: a
 * provider trip that catalog sync has not reconciled has no catalog id, and this says so instead
 * of minting one.
 *
 * <p>Backed by the {@code (provider_type, provider_trip_id)} unique constraint the mapping table
 * already carries, so the answer is unambiguous by construction.
 *
 * <p>Raises {@link com.roadscanner.inventoryservice.domain.exception.ProviderTripNotMappedException}
 * when no catalog trip has been reconciled for that provider trip.
 */
public interface ResolveProviderTrip {

    Result resolve(Command command);

    record Command(ProviderType providerType, String providerTripId) {
        public Command {
            Objects.requireNonNull(providerType, "providerType must not be null");
            if (providerTripId == null || providerTripId.isBlank()) {
                throw new IllegalArgumentException("providerTripId must not be blank");
            }
            providerTripId = providerTripId.strip();
        }
    }

    record Result(ProviderMapping mapping) {
        public Result {
            Objects.requireNonNull(mapping, "mapping must not be null");
        }
    }
}
