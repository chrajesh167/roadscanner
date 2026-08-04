package com.roadscanner.providerintegrationservice.domain.model;

import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.Objects;

/**
 * One traveller, in the shape providers actually require to issue a ticket. Passenger
 * identity/profile management is {@code user-service}'s concern — this is a pass-through carrier of
 * exactly what a provider needs to confirm a booking, nothing more.
 *
 * <p>Given and family name are held <strong>separately</strong> rather than as one display name.
 * Providers put them in distinct fields on the ticket, and deriving them by splitting a single
 * string is not a formatting nicety — it decides what is printed on a travel document that has to
 * match an ID at boarding. Any split rule ("the last token is the surname") is wrong for a large
 * share of real names, and wrong silently: the booking succeeds and the traveller is turned away.
 * So the caller supplies both, or the booking is refused here.
 *
 * <p>{@code birthDate} rather than an age, for the same reason: providers ask for a date of birth,
 * an age is a lossy projection of one, and a stored age is wrong within a year.
 */
public record PassengerDetail(String firstName, String lastName, LocalDate birthDate, Gender gender,
                              SeatNumber seatNumber) {

    public PassengerDetail {
        firstName = requireNonBlank(firstName, "firstName");
        // Non-blank deliberately: providers reject an empty family name, and a caller holding only
        // one name must decide what to send rather than have this quietly invent it.
        lastName = requireNonBlank(lastName, "lastName");
        Objects.requireNonNull(birthDate, "birthDate must not be null");
        Objects.requireNonNull(gender, "gender must not be null");
        Objects.requireNonNull(seatNumber, "seatNumber must not be null");
    }

    /** Age on the travel date, which is what fare rules are keyed on — not age today. */
    public int ageOn(LocalDate travelDate) {
        Objects.requireNonNull(travelDate, "travelDate must not be null");
        if (travelDate.isBefore(birthDate)) {
            throw new IllegalArgumentException("travelDate must not precede birthDate");
        }
        return Period.between(birthDate, travelDate).getYears();
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    /**
     * A closed vocabulary rather than free text: providers accept a fixed set, and seat maps
     * express restrictions ("ladies seat") in the same terms. A free string would travel to the
     * provider as whatever a caller typed and be rejected far from the mistake that caused it.
     */
    public enum Gender {
        MALE,
        FEMALE;

        public static Gender parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("gender must not be blank");
            }
            return valueOf(raw.strip().toUpperCase(Locale.ROOT));
        }

        public String wireValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
