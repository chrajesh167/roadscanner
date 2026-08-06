package com.roadscanner.bookingservice.adapter.in.rest.booking;

import com.roadscanner.bookingservice.domain.model.Passenger;

import java.time.LocalDate;

/**
 * A traveller on a booking, as returned to the customer.
 *
 * <p>{@code fullName} is retained alongside the parts because that is what a confirmation screen
 * prints; it is composed from them, never the source of them.
 */
public record PassengerResponse(String firstName, String lastName, String fullName, LocalDate birthDate,
                                 String gender, String seatNumber) {

    public static PassengerResponse from(Passenger passenger) {
        return new PassengerResponse(passenger.firstName(), passenger.lastName(), passenger.fullName(),
                passenger.birthDate(), passenger.gender(), passenger.seatNumber());
    }
}
