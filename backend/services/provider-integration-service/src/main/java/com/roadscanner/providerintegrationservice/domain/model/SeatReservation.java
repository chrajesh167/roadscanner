package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The result of a {@code BlockSeat} call — a temporary hold against the provider, not against
 * this platform's own inventory (that's {@code inventory-service}'s hold, a separate concept;
 * see docs/architecture/seat-locking-flow.md). Not persisted by this service (it owns no booking
 * state) — {@code inventory-service} is expected to track {@link #reservationId()} against its
 * own hold if it needs to correlate the two.
 */
public final class SeatReservation {

    private final ReservationId reservationId;
    private final String providerBlockReference;
    private final String providerTripId;
    private final List<SeatNumber> seatNumbers;
    private ReservationStatus status;
    private final Instant blockedAt;
    private final Instant expiresAt;

    private SeatReservation(ReservationId reservationId, String providerBlockReference, String providerTripId,
                             List<SeatNumber> seatNumbers, ReservationStatus status, Instant blockedAt, Instant expiresAt) {
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId must not be null");
        this.providerBlockReference = requireNonBlank(providerBlockReference, "providerBlockReference");
        this.providerTripId = requireNonBlank(providerTripId, "providerTripId");
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            throw new IllegalArgumentException("seatNumbers must not be empty");
        }
        this.seatNumbers = List.copyOf(seatNumbers);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.blockedAt = Objects.requireNonNull(blockedAt, "blockedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static SeatReservation block(ReservationId reservationId, String providerBlockReference,
                                         String providerTripId, List<SeatNumber> seatNumbers, Instant blockedAt,
                                         Instant expiresAt) {
        return new SeatReservation(reservationId, providerBlockReference, providerTripId, seatNumbers,
                ReservationStatus.BLOCKED, blockedAt, expiresAt);
    }

    /** @return {@code true} if this call changed state, {@code false} if already terminal
     * (RELEASED/CONFIRMED/EXPIRED) — an idempotent no-op for a repeated release call. */
    public boolean release() {
        if (status != ReservationStatus.BLOCKED) {
            return false;
        }
        this.status = ReservationStatus.RELEASED;
        return true;
    }

    /** Only a still-{@code BLOCKED}, not-yet-expired reservation can be confirmed into a
     * booking — the caller is expected to have checked {@link #isExpired(Instant)} first, but
     * this is enforced here too as the single source of truth for the rule. */
    public void confirm(Instant now) {
        if (status != ReservationStatus.BLOCKED) {
            throw new IllegalStateException("Cannot confirm a reservation that is not BLOCKED: " + status);
        }
        if (isExpired(now)) {
            throw new IllegalStateException("Cannot confirm a reservation that has expired");
        }
        this.status = ReservationStatus.CONFIRMED;
    }

    public boolean isExpired(Instant now) {
        return status == ReservationStatus.BLOCKED && !now.isBefore(expiresAt);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public ReservationId reservationId() {
        return reservationId;
    }

    public String providerBlockReference() {
        return providerBlockReference;
    }

    public String providerTripId() {
        return providerTripId;
    }

    public List<SeatNumber> seatNumbers() {
        return seatNumbers;
    }

    public ReservationStatus status() {
        return status;
    }

    public Instant blockedAt() {
        return blockedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
