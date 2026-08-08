package com.roadscanner.providerintegrationservice.adapter.in.rest.booking;

import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What a caller needs to act on a confirmed order afterwards.
 *
 * <p>{@code providerOrderReference} is the handle cancellation and order lookups are keyed by, and
 * {@code providerCheckoutReference} is the only handle support can use if the order lookup itself
 * ever fails. Both were captured at confirmation and then dropped here, which is what left
 * {@code booking-service} holding a booking it could not act on.
 *
 * <p>The order <strong>token</strong> is deliberately not returned. It is a provider credential,
 * and no caller has a use for one: cancellation is requested by order reference and the token is
 * resolved inside this service. Returning it would widen a secret's blast radius for nothing.
 */
public record BookingConfirmationResponse(String bookingReference, String reservationId, String providerTripId,
                                           List<String> passengerNames, BigDecimal totalFareAmount,
                                           String totalFareCurrency, String providerCheckoutReference,
                                           String providerOrderReference, Instant confirmedAt) {

    public static BookingConfirmationResponse from(BookingConfirmation confirmation) {
        return new BookingConfirmationResponse(confirmation.bookingReference().value(),
                confirmation.reservationId().toString(), confirmation.providerTripId(),
                confirmation.passengers().stream().map(com.roadscanner.providerintegrationservice.domain.model.PassengerDetail::fullName).toList(),
                confirmation.totalFare().amount(), confirmation.totalFare().currency().getCurrencyCode(),
                confirmation.providerCheckoutReference(), confirmation.providerOrderReference(),
                confirmation.confirmedAt());
    }
}
