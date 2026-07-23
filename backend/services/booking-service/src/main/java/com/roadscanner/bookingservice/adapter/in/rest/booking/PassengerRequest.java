package com.roadscanner.bookingservice.adapter.in.rest.booking;

import com.roadscanner.bookingservice.domain.port.in.CreateBooking;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PassengerRequest(@NotBlank String fullName, @Min(1) @Max(120) int age, @NotBlank String gender,
                                @NotBlank String seatNumber) {

    CreateBooking.PassengerInput toCommand() {
        return new CreateBooking.PassengerInput(fullName, age, gender, seatNumber);
    }
}
