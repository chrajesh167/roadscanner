package com.roadscanner.providerintegrationservice.adapter.in.rest.booking;

import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingConfirmationResponse(String bookingReference, String reservationId, String providerTripId,
                                           List<String> passengerNames, BigDecimal totalFareAmount,
                                           String totalFareCurrency, Instant confirmedAt) {

    public static BookingConfirmationResponse from(BookingConfirmation confirmation) {
        return new BookingConfirmationResponse(confirmation.bookingReference().value(),
                confirmation.reservationId().toString(), confirmation.providerTripId(),
                confirmation.passengers().stream().map(com.roadscanner.providerintegrationservice.domain.model.PassengerDetail::fullName).toList(),
                confirmation.totalFare().amount(), confirmation.totalFare().currency().getCurrencyCode(),
                confirmation.confirmedAt());
    }
}
