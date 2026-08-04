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
 * A traveller at the seat-block stage.
 *
 * <p>Identical in shape to {@link PassengerRequest} today, and kept separate on purpose: holding a
 * seat and issuing a ticket are different moments with different obligations, and merging them
 * would mean any future field a provider demands only at ticketing would silently become mandatory
 * to place a hold.
 */
public record BlockPassengerRequest(
        @NotBlank @Schema(example = "Asha") String firstName,
        @NotBlank @Schema(example = "Menon") String lastName,
        @NotNull @Past @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Schema(example = "1994-03-17")
        LocalDate birthDate,
        @NotBlank @Schema(allowableValues = {"male", "female"}, example = "female") String gender,
        @NotBlank @Schema(example = "12") String seatNumber) {

    public PassengerDetail toDomain() {
        return new PassengerDetail(firstName, lastName, birthDate, PassengerDetail.Gender.parse(gender),
                new SeatNumber(seatNumber));
    }
}
