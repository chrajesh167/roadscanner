package com.roadscanner.inventoryservice.testsupport.fakes;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.port.out.ProviderIntegrationClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiFunction;

/** A fully-configurable {@link ProviderIntegrationClient} test double. */
public final class StubProviderIntegrationClient implements ProviderIntegrationClient {

    public BiFunction<ProviderType, LocalDate, List<ExternalProviderTrip>> searchTripsResult = (p, d) -> List.of();
    public Optional<ExternalSeatLayout> seatLayoutResult = Optional.empty();
    public OptionalInt availableSeatCountResult = OptionalInt.empty();

    /**
     * What each search actually named its endpoints. Recorded because the defect was never in the
     * response — it was in the request: city names were sent where providers require the ids they
     * issued, and a stub that ignored the arguments is what let that survive a green suite.
     */
    public final List<String> searchedOrigins = new ArrayList<>();
    public final List<String> searchedDestinations = new ArrayList<>();

    @Override
    public List<ExternalProviderTrip> searchTrips(ProviderType providerType, String origin, String destination, LocalDate date) {
        searchedOrigins.add(origin);
        searchedDestinations.add(destination);
        return searchTripsResult.apply(providerType, date);
    }

    @Override
    public Optional<ExternalSeatLayout> getSeatLayout(ProviderType providerType, String providerTripId) {
        return seatLayoutResult;
    }

    @Override
    public OptionalInt getAvailableSeatCount(ProviderType providerType, String providerTripId) {
        return availableSeatCountResult;
    }
}
