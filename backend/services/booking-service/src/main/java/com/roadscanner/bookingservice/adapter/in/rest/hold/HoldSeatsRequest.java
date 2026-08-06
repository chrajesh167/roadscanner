package com.roadscanner.bookingservice.adapter.in.rest.hold;

import com.roadscanner.bookingservice.domain.model.Passenger;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Holding seats now requires knowing who occupies them.
 *
 * <p>The request previously carried a bare {@code seatNumbers} list. A provider binds a seat to
 * its occupant at block time — it needs the traveller's gender to honour gender-restricted seats —
 * so a list of seat numbers cannot express what is being asked for. Each passenger carries its own
 * seat, which also means the two can never fall out of step the way parallel lists do.
 */
public record HoldSeatsRequest(@NotNull UUID tripId,
                                @NotEmpty List<@Valid HoldPassengerRequest> passengers) {

    public List<Passenger> toPassengers() {
        return passengers.stream().map(HoldPassengerRequest::toDomain).toList();
    }

    /**
     * One traveller, in the shape the provider requires.
     *
     * <p>Given and family name are separate and both mandatory, and the birth date is a date
     * rather than an age. Neither can be derived from a display name or an age — a wrong guess
     * produces a booking that succeeds and a traveller who is turned away at boarding — so both
     * are collected from the traveller instead.
     */
    public record HoldPassengerRequest(
            @NotBlank @Schema(example = "Asha") String firstName,
            @NotBlank @Schema(example = "Menon") String lastName,
            @NotNull @Past @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Schema(example = "1994-03-17")
            LocalDate birthDate,
            @NotBlank @Schema(allowableValues = {"male", "female"}, example = "female") String gender,
            @NotBlank @Schema(example = "L1") String seatNumber) {

        Passenger toDomain() {
            return new Passenger(firstName, lastName, birthDate, gender, seatNumber);
        }
    }
}
