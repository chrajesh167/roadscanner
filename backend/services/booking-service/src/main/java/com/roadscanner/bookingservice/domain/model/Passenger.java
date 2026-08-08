package com.roadscanner.bookingservice.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One traveller on a booking, in the shape {@code provider-integration-service} actually accepts
 * ({@code PassengerRequest} / {@code BlockPassengerRequest}), which in turn mirrors what FlixBus
 * requires at checkout: given name, family name, birth date and gender.
 *
 * <p>This record previously held {@code (fullName, age, gender, seatNumber)} and claimed to be
 * "field-for-field identical" to the provider contract. It was not, and had not been since the
 * provider adapter was implemented — every confirm-booking call this service made would have been
 * rejected on all four fields. Nothing caught it because the end-to-end test stubs the provider
 * with a fixture that ignores the request body.
 *
 * <p><strong>Neither missing field can be derived.</strong> No split rule applied to a display
 * name is right for every real name, and an age cannot produce the birth date a provider prints on
 * a travel document that has to match an ID at boarding. Both are collected from the traveller
 * instead — the reason this change reaches customer-web rather than stopping at the adapter.
 */
public record Passenger(String firstName, String lastName, LocalDate birthDate, String gender,
                         String seatNumber) {

    public Passenger {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("firstName must not be blank");
        }
        // The provider rejects a blank family name outright, so accepting one here would only move
        // the failure to a point where the traveller has already paid.
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("lastName must not be blank");
        }
        Objects.requireNonNull(birthDate, "birthDate must not be null");
        if (gender == null || gender.isBlank()) {
            throw new IllegalArgumentException("gender must not be blank");
        }
        if (seatNumber == null || seatNumber.isBlank()) {
            throw new IllegalArgumentException("seatNumber must not be blank");
        }
        firstName = firstName.trim();
        lastName = lastName.trim();
        gender = gender.trim();
        seatNumber = seatNumber.trim();
    }

    /**
     * For display and for events only — never sent to a provider, which wants the two parts
     * separately. Composing here is safe in a way that splitting never is.
     */
    public String fullName() {
        return firstName + " " + lastName;
    }
}
