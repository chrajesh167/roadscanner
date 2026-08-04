package com.roadscanner.providerintegrationservice.adapter.in.rest.booking;

import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * One traveller on a booking request.
 *
 * <p>Given and family name are separate fields, and both are required. Providers print them in
 * distinct places on a travel document that has to match an ID at boarding, and no split rule
 * applied to a single display name is right for every real name — a wrong guess produces a booking
 * that succeeds and a traveller who is turned away.
 */
record PassengerRequest(
        @NotBlank @Schema(example = "Asha") String firstName,
        @NotBlank @Schema(example = "Menon") String lastName,
        @NotNull @Past @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Schema(example = "1994-03-17")
        LocalDate birthDate,
        @NotBlank @Schema(allowableValues = {"male", "female"}, example = "female") String gender,
        @NotBlank @Schema(example = "12") String seatNumber) {

    PassengerDetail toDomain() {
        return new PassengerDetail(firstName, lastName, birthDate, PassengerDetail.Gender.parse(gender),
                new SeatNumber(seatNumber));
    }
}
