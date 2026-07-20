package com.roadscanner.providerintegrationservice.domain.model;

/** The minimal passenger shape every provider's booking-confirmation API needs. Passenger
 * identity/profile management is {@code user-service}'s concern, not this service's — this is a
 * pass-through carrier of exactly what the provider requires to confirm a booking, nothing more. */
public record PassengerDetail(String fullName, int age, String gender, SeatNumber seatNumber) {

    public PassengerDetail {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
        if (age <= 0 || age > 120) {
            throw new IllegalArgumentException("age must be a plausible human age");
        }
        if (gender == null || gender.isBlank()) {
            throw new IllegalArgumentException("gender must not be blank");
        }
        java.util.Objects.requireNonNull(seatNumber, "seatNumber must not be null");
    }
}
