package com.roadscanner.bookingservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatHoldTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private SeatHold newHold(Instant expiresAt) {
        return SeatHold.create(SeatHoldId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", List.of("L1"),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), expiresAt, T0);
    }

    @Test
    void isExpiredComparesAgainstExpiresAt() {
        SeatHold hold = newHold(T0.plusSeconds(600));

        assertThat(hold.isExpired(T0.plusSeconds(500))).isFalse();
        assertThat(hold.isExpired(T0.plusSeconds(600))).isTrue();
        assertThat(hold.isExpired(T0.plusSeconds(700))).isTrue();
    }

    @Test
    void isOwnedByComparesTravelerId() {
        UUID travelerId = UUID.randomUUID();
        SeatHold hold = SeatHold.create(SeatHoldId.generate(), travelerId, new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", List.of("L1"),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0.plusSeconds(600), T0);

        assertThat(hold.isOwnedBy(travelerId)).isTrue();
        assertThat(hold.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void createRejectsEmptySeatNumbers() {
        assertThatThrownBy(() -> SeatHold.create(SeatHoldId.generate(), UUID.randomUUID(),
                new TripId(UUID.randomUUID()), T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1",
                "block-ref-1", List.of(), new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")),
                T0.plusSeconds(600), T0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
