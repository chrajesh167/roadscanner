package com.roadscanner.providerintegrationservice.adapter.in.rest.booking;

import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PassengerRequest(@NotBlank String fullName, @Min(1) @Max(120) int age, @NotBlank String gender,
                                @NotBlank String seatNumber) {

    PassengerDetail toDomain() {
        return new PassengerDetail(fullName, age, gender, new SeatNumber(seatNumber));
    }
}
