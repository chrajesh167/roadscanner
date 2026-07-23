package com.roadscanner.bookingservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One row of {@code booking_passengers} — fixed at booking creation, never mutated
 * (docs/services/booking-service/domain-model.md's {@code Passenger}). */
@Embeddable
public class PassengerEmbeddable {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "age", nullable = false)
    private int age;

    @Column(name = "gender", nullable = false)
    private String gender;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    protected PassengerEmbeddable() {
    }

    public PassengerEmbeddable(String fullName, int age, String gender, String seatNumber) {
        this.fullName = fullName;
        this.age = age;
        this.gender = gender;
        this.seatNumber = seatNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public int getAge() {
        return age;
    }

    public String getGender() {
        return gender;
    }

    public String getSeatNumber() {
        return seatNumber;
    }
}
