package com.roadscanner.bookingservice.adapter.in.rest.booking;

import com.roadscanner.bookingservice.domain.model.Passenger;

public record PassengerResponse(String fullName, int age, String gender, String seatNumber) {

    public static PassengerResponse from(Passenger passenger) {
        return new PassengerResponse(passenger.fullName(), passenger.age(), passenger.gender(), passenger.seatNumber());
    }
}
