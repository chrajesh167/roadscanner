package com.roadscanner.bookingservice.adapter.in.rest.booking;

import com.roadscanner.bookingservice.domain.port.in.CreateBooking;

public record CreateBookingResponse(String bookingId, String status) {

    public static CreateBookingResponse from(CreateBooking.Result result) {
        return new CreateBookingResponse(result.bookingId().toString(), result.status().name());
    }
}
