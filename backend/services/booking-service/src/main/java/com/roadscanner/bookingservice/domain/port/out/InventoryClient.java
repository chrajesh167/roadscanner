package com.roadscanner.bookingservice.domain.port.out;

import com.roadscanner.bookingservice.domain.model.ProviderType;
import com.roadscanner.bookingservice.domain.model.TripId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The only way this service ever learns catalog facts — every call crosses to
 * {@code inventory-service}'s existing, frozen REST API
 * (docs/services/booking-service/boundaries.md's "Relationship to `inventory-service`"). Mirrors
 * three of that service's real endpoints exactly: {@code GET /trips/{tripId}},
 * {@code GET /trips/{tripId}/seat-layout}, {@code GET /trips/{tripId}/provider-mapping}.
 *
 * <p>Unlike {@code inventory-service}'s own "degrade, not fail" posture toward
 * {@code search-service}, every method here either returns the fact or throws
 * {@code UpstreamServiceUnavailableException} — this service must fail the operation rather than
 * proceed against unverifiable catalog data (NFR-7,
 * docs/services/booking-service/boundaries.md's "Failure mode").
 */
public interface InventoryClient {

    /** Empty if the trip genuinely doesn't exist (a {@code 404} from {@code inventory-service}) —
     * distinct from "unreachable," which throws. */
    Optional<TripSnapshot> getTrip(TripId tripId);

    Optional<SeatLayoutView> getSeatLayout(TripId tripId);

    /** Empty if the trip has no {@code ProviderMapping} — a first-party trip with no live-
     * booking path (docs/services/booking-service/use-cases.md's "A Trip With No
     * `ProviderMapping` Cannot Be Held"), not an error. */
    Optional<ProviderMappingView> getProviderMapping(TripId tripId);

    record TripSnapshot(TripId tripId, String origin, String destination, Instant departureTime,
                         Instant arrivalTime, String operatorDisplayName, String busTypeCategory,
                         List<String> amenities, BigDecimal fareAmount, String fareCurrency, boolean bookable) {
    }

    record SeatLayoutView(List<SeatShape> seats) {
    }

    /** Static shape only — deliberately no status field, matching {@code inventory-service}'s
     * own {@code SeatResponse}. */
    record SeatShape(String seatNumber, String deck, String seatType, boolean wheelchairAccessible,
                      Integer position) {
    }

    record ProviderMappingView(ProviderType providerType, String providerTripId) {
    }
}
