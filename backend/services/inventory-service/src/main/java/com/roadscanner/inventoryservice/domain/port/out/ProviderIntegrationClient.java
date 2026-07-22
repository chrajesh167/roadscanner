package com.roadscanner.inventoryservice.domain.port.out;

import com.roadscanner.inventoryservice.domain.model.ProviderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The only way this service ever learns anything live — every call crosses to
 * {@code provider-integration-service}'s canonical, provider-agnostic REST API
 * (docs/services/inventory-service/boundaries.md). No implementation of this port may import a
 * provider-specific type; that rule is enforced by {@code provider-integration-service} itself
 * being the only service with provider-specific adapters at all.
 *
 * Every method degrades on failure (empty result / {@code OptionalInt.empty()}), never throws —
 * matching {@code search-service}'s {@code AvailabilityClient} "degrade, not fail" contract, one
 * hop further down the call chain (docs/services/inventory-service/boundaries.md's failure-mode
 * section).
 */
public interface ProviderIntegrationClient {

    List<ExternalProviderTrip> searchTrips(ProviderType providerType, String origin, String destination, LocalDate date);

    Optional<ExternalSeatLayout> getSeatLayout(ProviderType providerType, String providerTripId);

    OptionalInt getAvailableSeatCount(ProviderType providerType, String providerTripId);

    /** The provider's own search result, already stripped of anything this service must not
     * store — no live status of any kind. */
    record ExternalProviderTrip(String providerTripId, String operatorName, String origin, String destination,
                                 Instant departureTime, Instant arrivalTime, String busType,
                                 BigDecimal fareAmount, String fareCurrency, int seatsAvailable) {
    }

    /** The provider's own seat map, reduced to static shape only — {@code status} is
     * deliberately not carried across this boundary at all (docs/services/inventory-service/domain-model.md's
     * {@code SeatLayout} entry: this service stores no seat status, ever). */
    record ExternalSeat(String seatNumber, String deck, String seatType) {
    }

    record ExternalSeatLayout(String providerTripId, List<ExternalSeat> seats) {
    }
}
