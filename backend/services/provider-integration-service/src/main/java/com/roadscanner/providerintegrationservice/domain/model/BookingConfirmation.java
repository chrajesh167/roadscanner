package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A confirmed booking, together with every provider-side handle needed to act on it later.
 *
 * <p>Providers commonly mint more than one identifier during a booking: a checkout handle that
 * exists before the order does, an order id, and a separate token that authorises reads and
 * cancellation of that order. All three are captured because each is required by a different later
 * call, and none can be re-derived — an order reference without its token cannot be cancelled, so
 * dropping the token silently converts a cancellable booking into a support ticket.
 *
 * <p>Named for the role each plays rather than for one provider's word for it, so the next
 * provider's equivalents land in the same fields.
 *
 * @param providerCheckoutReference the pre-order handle, if the provider issues one
 * @param providerOrderReference    the provider's id for the resulting order
 * @param providerOrderToken        the secret authorising later reads/cancellation of that order
 */
public record BookingConfirmation(BookingReference bookingReference, ReservationId reservationId,
                                  String providerTripId, List<PassengerDetail> passengers, FareAmount totalFare,
                                  String providerCheckoutReference, String providerOrderReference,
                                  String providerOrderToken, Instant confirmedAt) {

    public BookingConfirmation {
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        if (providerTripId == null || providerTripId.isBlank()) {
            throw new IllegalArgumentException("providerTripId must not be blank");
        }
        if (passengers == null || passengers.isEmpty()) {
            throw new IllegalArgumentException("passengers must not be empty");
        }
        passengers = List.copyOf(passengers);
        Objects.requireNonNull(totalFare, "totalFare must not be null");
        Objects.requireNonNull(confirmedAt, "confirmedAt must not be null");
    }

    /** Absent for a provider that issues no separate pre-order handle. */
    public Optional<String> providerCheckoutReferenceIfPresent() {
        return Optional.ofNullable(providerCheckoutReference);
    }

    /**
     * Present only where the provider requires a token alongside the order id. Absent is a real
     * state, not a defect — but a caller attempting cancellation must treat a missing token as
     * "cannot cancel through this provider" rather than sending an empty one.
     */
    public Optional<String> providerOrderTokenIfPresent() {
        return Optional.ofNullable(providerOrderToken);
    }
}
