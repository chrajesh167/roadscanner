package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The result of a {@code BlockSeat} call — a temporary hold against the provider, not against this
 * platform's own inventory (that's {@code inventory-service}'s hold, a separate concept; see
 * docs/architecture/seat-locking-flow.md).
 *
 * <p><strong>Now persisted by this service.</strong> It previously was not, on the reasoning that
 * this service owns no booking state. That held only while a block was a single provider call that
 * could be described by one reference. A real provider block is several calls that mint several
 * identifiers — a container, a per-passenger ticket handle, a per-seat id — and confirming or
 * cancelling later requires the exact values the provider issued. Nothing recovers them after the
 * fact, so the correlation is written down at the moment it exists. {@code inventory-service} still
 * tracks {@link #reservationId()} against its own hold to correlate the two.
 */
public final class SeatReservation {

    private final ReservationId reservationId;
    private final ProviderType providerType;
    private final String providerBlockReference;
    private final String providerTripId;
    private final List<SeatAssignment> seatAssignments;
    private ReservationStatus status;
    private final Instant blockedAt;
    private final Instant expiresAt;

    private SeatReservation(ReservationId reservationId, ProviderType providerType, String providerBlockReference,
                            String providerTripId, List<SeatAssignment> seatAssignments, ReservationStatus status,
                            Instant blockedAt, Instant expiresAt) {
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId must not be null");
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        this.providerBlockReference = requireNonBlank(providerBlockReference, "providerBlockReference");
        this.providerTripId = requireNonBlank(providerTripId, "providerTripId");
        if (seatAssignments == null || seatAssignments.isEmpty()) {
            throw new IllegalArgumentException("seatAssignments must not be empty");
        }
        this.seatAssignments = List.copyOf(seatAssignments);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.blockedAt = Objects.requireNonNull(blockedAt, "blockedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static SeatReservation block(ReservationId reservationId, ProviderType providerType,
                                        String providerBlockReference, String providerTripId,
                                        List<SeatAssignment> seatAssignments, Instant blockedAt, Instant expiresAt) {
        return new SeatReservation(reservationId, providerType, providerBlockReference, providerTripId,
                seatAssignments, ReservationStatus.BLOCKED, blockedAt, expiresAt);
    }

    public static SeatReservation reconstitute(ReservationId reservationId, ProviderType providerType,
                                               String providerBlockReference, String providerTripId,
                                               List<SeatAssignment> seatAssignments, ReservationStatus status,
                                               Instant blockedAt, Instant expiresAt) {
        return new SeatReservation(reservationId, providerType, providerBlockReference, providerTripId,
                seatAssignments, status, blockedAt, expiresAt);
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

    public List<SeatNumber> seatNumbers() {
        return seatAssignments.stream().map(SeatAssignment::seatNumber).toList();
    }

    /** The provider's ticket handles, in the order the seats were assigned. */
    public List<String> providerTicketIds() {
        return seatAssignments.stream().map(SeatAssignment::providerTicketId).toList();
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

    public ProviderType providerType() {
        return providerType;
    }

    public String providerBlockReference() {
        return providerBlockReference;
    }

    public String providerTripId() {
        return providerTripId;
    }

    public List<SeatAssignment> seatAssignments() {
        return seatAssignments;
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
