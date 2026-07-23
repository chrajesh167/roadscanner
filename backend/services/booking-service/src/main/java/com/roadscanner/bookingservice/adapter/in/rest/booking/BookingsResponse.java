package com.roadscanner.bookingservice.adapter.in.rest.booking;

import com.roadscanner.bookingservice.domain.model.Booking;

import java.util.List;

public record BookingsResponse(List<BookingResponse> bookings) {

    public static BookingsResponse from(List<Booking> bookings) {
        return new BookingsResponse(bookings.stream().map(BookingResponse::from).toList());
    }
}
