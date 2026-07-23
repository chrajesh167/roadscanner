package com.roadscanner.bookingservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private Booking newBooking() {
        return Booking.create(BookingId.generate(), UUID.randomUUID(), new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);
    }

    @Test
    void createStartsPendingPayment() {
        Booking booking = newBooking();

        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.createdAt()).isEqualTo(T0);
        assertThat(booking.confirmedAt()).isEmpty();
        assertThat(booking.cancellationReason()).isEmpty();
    }

    @Test
    void confirmTransitionsFromPendingPaymentAndSetsTicketAndReference() {
        Booking booking = newBooking();
        Ticket ticket = new Ticket("ticket-1", "PDF", "content".getBytes(), T0.plusSeconds(700));

        boolean applied = booking.confirm("provider-booking-ref-1", ticket, T0.plusSeconds(700));

        assertThat(applied).isTrue();
        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.providerBookingReference()).contains("provider-booking-ref-1");
        assertThat(booking.ticket()).contains(ticket);
        assertThat(booking.confirmedAt()).contains(T0.plusSeconds(700));
    }

    @Test
    void confirmIsANoOpWhenNotPendingPayment() {
        Booking booking = newBooking();
        Ticket ticket = new Ticket("ticket-1", "PDF", "content".getBytes(), T0.plusSeconds(700));
        booking.confirm("provider-booking-ref-1", ticket, T0.plusSeconds(700));

        boolean secondApply = booking.confirm("provider-booking-ref-2", ticket, T0.plusSeconds(800));

        assertThat(secondApply).isFalse();
        assertThat(booking.providerBookingReference()).contains("provider-booking-ref-1");
    }

    @Test
    void cancelFromPendingPaymentSetsReasonAndTimestamp() {
        Booking booking = newBooking();

        boolean applied = booking.cancel(CancellationReason.PAYMENT_FAILED, T0.plusSeconds(100));

        assertThat(applied).isTrue();
        assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.cancellationReason()).contains(CancellationReason.PAYMENT_FAILED);
        assertThat(booking.cancelledAt()).contains(T0.plusSeconds(100));
    }

    @Test
    void cancelFromConfirmedSucceeds() {
        Booking booking = newBooking();
        booking.confirm("ref", new Ticket("t", "PDF", "c".getBytes(), T0), T0.plusSeconds(50));

        boolean applied = booking.cancel(CancellationReason.TRAVELER_REQUESTED, T0.plusSeconds(200));

        assertThat(applied).isTrue();
        assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelIsIdempotentAndNeverOverwritesTheReason() {
        Booking booking = newBooking();
        booking.cancel(CancellationReason.PAYMENT_FAILED, T0.plusSeconds(100));

        boolean secondApply = booking.cancel(CancellationReason.TRAVELER_REQUESTED, T0.plusSeconds(200));

        assertThat(secondApply).isFalse();
        assertThat(booking.cancellationReason()).contains(CancellationReason.PAYMENT_FAILED);
        assertThat(booking.cancelledAt()).contains(T0.plusSeconds(100));
    }

    @Test
    void cancelIsANoOpOnceCompleted() {
        Booking booking = newBooking();
        booking.confirm("ref", new Ticket("t", "PDF", "c".getBytes(), T0), T0.plusSeconds(50));
        booking.complete(T0.plusSeconds(9999));

        boolean applied = booking.cancel(CancellationReason.TRAVELER_REQUESTED, T0.plusSeconds(10000));

        assertThat(applied).isFalse();
        assertThat(booking.status()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void completeOnlyAppliesFromConfirmed() {
        Booking booking = newBooking();

        boolean appliedTooEarly = booking.complete(T0.plusSeconds(50));
        assertThat(appliedTooEarly).isFalse();

        booking.confirm("ref", new Ticket("t", "PDF", "c".getBytes(), T0), T0.plusSeconds(50));
        boolean applied = booking.complete(T0.plusSeconds(9999));

        assertThat(applied).isTrue();
        assertThat(booking.status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.completedAt()).contains(T0.plusSeconds(9999));
    }

    @Test
    void isHoldExpiredOnlyMeaningfulWhilePendingPayment() {
        Booking booking = newBooking();

        assertThat(booking.isHoldExpired(T0.plusSeconds(700))).isTrue();
        assertThat(booking.isHoldExpired(T0.plusSeconds(100))).isFalse();

        booking.confirm("ref", new Ticket("t", "PDF", "c".getBytes(), T0), T0.plusSeconds(50));
        assertThat(booking.isHoldExpired(T0.plusSeconds(700))).isFalse();
    }

    @Test
    void isOwnedByComparesTravelerId() {
        UUID travelerId = UUID.randomUUID();
        Booking booking = Booking.create(BookingId.generate(), travelerId, new TripId(UUID.randomUUID()),
                T0.plusSeconds(3600), new ProviderType("MOCK"), "MOCK-TRIP-1", "block-ref-1", T0.plusSeconds(600),
                List.of(new Passenger("Jane Doe", 30, "F", "L1")),
                new Fare(BigDecimal.valueOf(500), Currency.getInstance("INR")), T0);

        assertThat(booking.isOwnedBy(travelerId)).isTrue();
        assertThat(booking.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void markSupportFlaggedSetsFlag() {
        Booking booking = newBooking();

        booking.markSupportFlagged();

        assertThat(booking.supportFlagged()).isTrue();
    }
}
