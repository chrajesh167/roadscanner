package com.roadscanner.providerintegrationservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatReservationTest {

    private static final Instant BLOCKED_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant EXPIRES_AT = BLOCKED_AT.plusSeconds(300);

    private SeatReservation blocked() {
        return SeatReservation.block(ReservationId.generate(), "PROV-REF-1", "TRIP-1",
                List.of(new SeatNumber("L1")), BLOCKED_AT, EXPIRES_AT);
    }

    @Test
    void releaseIsIdempotent() {
        SeatReservation reservation = blocked();

        assertThat(reservation.release()).isTrue();
        assertThat(reservation.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.release()).isFalse();
    }

    @Test
    void confirmRejectsAlreadyReleasedReservation() {
        SeatReservation reservation = blocked();
        reservation.release();

        assertThatThrownBy(() -> reservation.confirm(BLOCKED_AT.plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirmRejectsExpiredReservation() {
        SeatReservation reservation = blocked();

        assertThatThrownBy(() -> reservation.confirm(EXPIRES_AT.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirmSucceedsWhileStillBlockedAndNotExpired() {
        SeatReservation reservation = blocked();

        reservation.confirm(BLOCKED_AT.plusSeconds(10));

        assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }
}
