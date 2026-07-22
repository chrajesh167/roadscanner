package com.roadscanner.inventoryservice.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatLayoutTest {

    @Test
    void rejectsAnEmptySeatList() {
        assertThatThrownBy(() -> new SeatLayout(TripId.generate(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seatCountReflectsTheNumberOfSeats() {
        Seat seatA = new Seat(new SeatNumber("L1"), "LOWER", "SLEEPER", false, 1);
        Seat seatB = new Seat(new SeatNumber("L2"), "LOWER", "SLEEPER", true, 2);

        SeatLayout layout = new SeatLayout(TripId.generate(), List.of(seatA, seatB));

        assertThat(layout.seatCount()).isEqualTo(2);
    }
}
