package com.roadscanner.bookingservice.adapter.in.rest.booking;

import com.roadscanner.bookingservice.domain.model.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingResponse(String bookingId, String travelerId, String tripId, String providerBookingReference,
                               List<PassengerResponse> passengers, BigDecimal fareAmount, String fareCurrency,
                               String status, String cancellationReason, boolean supportFlagged, Instant createdAt,
                               Instant confirmedAt, Instant cancelledAt, Instant completedAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.id().toString(),
                booking.travelerId().toString(),
                booking.tripId().toString(),
                booking.providerBookingReference().orElse(null),
                booking.passengers().stream().map(PassengerResponse::from).toList(),
                booking.fare().amount(),
                booking.fare().currency().getCurrencyCode(),
                booking.status().name(),
                booking.cancellationReason().map(Enum::name).orElse(null),
                booking.supportFlagged(),
                booking.createdAt(),
                booking.confirmedAt().orElse(null),
                booking.cancelledAt().orElse(null),
                booking.completedAt().orElse(null)
        );
    }
}
