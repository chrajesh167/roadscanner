package com.roadscanner.bookingservice.domain.model;

/**
 * Deliberately field-for-field identical to {@code provider-integration-service}'s
 * {@code PassengerDetail}/{@code PassengerRequest} shape
 * (docs/services/provider-integration-service/domain-model.md), the same "no translation needed
 * at the boundary" discipline already established between {@code inventory-service}'s
 * {@code CatalogTripEventMessage} and {@code search-service}'s {@code TripEventMessage}. This
 * list is passed straight through to {@code ConfirmBooking} without remapping field names
 * (docs/services/booking-service/domain-model.md).
 */
public record Passenger(String fullName, int age, String gender, String seatNumber) {

    public Passenger {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
        if (age < 1 || age > 120) {
            throw new IllegalArgumentException("age must be between 1 and 120");
        }
        if (gender == null || gender.isBlank()) {
            throw new IllegalArgumentException("gender must not be blank");
        }
        if (seatNumber == null || seatNumber.isBlank()) {
            throw new IllegalArgumentException("seatNumber must not be blank");
        }
    }
}
