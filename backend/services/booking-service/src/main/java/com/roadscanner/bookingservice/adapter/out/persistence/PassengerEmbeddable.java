package com.roadscanner.bookingservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;

/**
 * One row of {@code booking_passengers} / {@code seat_hold_passengers} — fixed when the seats are
 * held, never mutated (docs/services/booking-service/domain-model.md's {@code Passenger}).
 *
 * <p>Stores the given and family name separately and a birth date rather than an age, because that
 * is what the provider requires and what a travel document has to match. V2 migrates the previous
 * {@code full_name}/{@code age} columns.
 */
@Embeddable
public class PassengerEmbeddable {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "gender", nullable = false)
    private String gender;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    protected PassengerEmbeddable() {
    }

    public PassengerEmbeddable(String firstName, String lastName, LocalDate birthDate, String gender,
                                String seatNumber) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.gender = gender;
        this.seatNumber = seatNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getGender() {
        return gender;
    }

    public String getSeatNumber() {
        return seatNumber;
    }
}
