package com.roadscanner.bookingservice.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The aggregate root this whole service exists to own — the platform's only source of truth for
 * a booking record (docs/services/booking-service/domain-model.md). Owns exactly the state
 * machine {@code docs/architecture/booking-flow.md}'s frozen diagram defines; every transition
 * method below is idempotent, matching {@code docs/services/booking-service/booking-state-machine.md}'s
 * "every transition must be idempotent" requirement — a transition attempted from a state it
 * doesn't apply to is a safe no-op (returns {@code false}), never an exception, since every
 * trigger on this platform can be redelivered at-least-once.
 */
public final class Booking {

    private final BookingId id;
    private final UUID travelerId;
    private final TripId tripId;
    private final Instant tripDepartureTime;
    private final ProviderType providerType;
    private final String providerTripId;
    private final String providerBlockReference;
    private final Instant holdExpiresAt;
    private String providerBookingReference;
    private final List<Passenger> passengers;
    private final Fare fare;
    private BookingStatus status;
    private CancellationReason cancellationReason;
    private boolean supportFlagged;
    private String paymentReference;
    private Ticket ticket;
    private final Instant createdAt;
    private Instant confirmedAt;
    private Instant cancelledAt;
    private Instant completedAt;

    private Booking(BookingId id, UUID travelerId, TripId tripId, Instant tripDepartureTime,
                     ProviderType providerType, String providerTripId, String providerBlockReference,
                     Instant holdExpiresAt, String providerBookingReference, List<Passenger> passengers, Fare fare,
                     BookingStatus status, CancellationReason cancellationReason, boolean supportFlagged,
                     String paymentReference, Ticket ticket, Instant createdAt, Instant confirmedAt,
                     Instant cancelledAt, Instant completedAt) {
        this.id = id;
        this.travelerId = travelerId;
        this.tripId = tripId;
        this.tripDepartureTime = tripDepartureTime;
        this.providerType = providerType;
        this.providerTripId = providerTripId;
        this.providerBlockReference = providerBlockReference;
        this.holdExpiresAt = holdExpiresAt;
        this.providerBookingReference = providerBookingReference;
        this.passengers = List.copyOf(passengers);
        this.fare = fare;
        this.status = status;
        this.cancellationReason = cancellationReason;
        this.supportFlagged = supportFlagged;
        this.paymentReference = paymentReference;
        this.ticket = ticket;
        this.createdAt = createdAt;
        this.confirmedAt = confirmedAt;
        this.cancelledAt = cancelledAt;
        this.completedAt = completedAt;
    }

    /** {@code Create Booking} — a {@link Booking} row does not exist until it is, by construction,
     * awaiting payment (docs/services/booking-service/domain-model.md's {@code BookingStatus}
     * entry: "there is no separate 'just created, not yet awaiting payment' state"). */
    public static Booking create(BookingId id, UUID travelerId, TripId tripId, Instant tripDepartureTime,
                                  ProviderType providerType, String providerTripId, String providerBlockReference,
                                  Instant holdExpiresAt, List<Passenger> passengers, Fare fare, Instant now) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(travelerId, "travelerId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(tripDepartureTime, "tripDepartureTime must not be null");
        Objects.requireNonNull(providerType, "providerType must not be null");
        Objects.requireNonNull(providerTripId, "providerTripId must not be null");
        Objects.requireNonNull(providerBlockReference, "providerBlockReference must not be null");
        Objects.requireNonNull(holdExpiresAt, "holdExpiresAt must not be null");
        Objects.requireNonNull(fare, "fare must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (passengers == null || passengers.isEmpty()) {
            throw new IllegalArgumentException("passengers must not be empty");
        }
        return new Booking(id, travelerId, tripId, tripDepartureTime, providerType, providerTripId,
                providerBlockReference, holdExpiresAt, null, passengers, fare, BookingStatus.PENDING_PAYMENT, null,
                false, null, null, now, null, null, null);
    }

    public static Booking reconstitute(BookingId id, UUID travelerId, TripId tripId, Instant tripDepartureTime,
                                        ProviderType providerType, String providerTripId,
                                        String providerBlockReference, Instant holdExpiresAt,
                                        String providerBookingReference, List<Passenger> passengers, Fare fare,
                                        BookingStatus status, CancellationReason cancellationReason,
                                        boolean supportFlagged, String paymentReference, Ticket ticket,
                                        Instant createdAt, Instant confirmedAt, Instant cancelledAt,
                                        Instant completedAt) {
        return new Booking(id, travelerId, tripId, tripDepartureTime, providerType, providerTripId,
                providerBlockReference, holdExpiresAt,
                providerBookingReference, passengers, fare, status, cancellationReason, supportFlagged,
                paymentReference, ticket, createdAt, confirmedAt, cancelledAt, completedAt);
    }

    /** Whether the reservation this (still {@code PENDING_PAYMENT}) booking was created from has
     * passed its provider-side TTL — the same {@code expiresAt} the originating {@code SeatHold}
     * carried, preserved here because the reservation's TTL doesn't stop applying just because
     * this service has converted its own bookkeeping from a hold to a booking. Backs
     * {@code Sweep Stale Holds}' defensive check for a hold that expired with no
     * {@code SeatReleased} ever arriving. */
    public boolean isHoldExpired(Instant now) {
        return status == BookingStatus.PENDING_PAYMENT && !now.isBefore(holdExpiresAt);
    }

    /** {@code PENDING_PAYMENT -> CONFIRMED}. Returns {@code false} (no-op) if not currently
     * {@code PENDING_PAYMENT} — covers both a duplicate {@code PaymentCompleted} delivery for an
     * already-{@code CONFIRMED} booking, and the "late success after a timeout-driven
     * cancellation" edge case, where the caller must branch on the {@code false} result and
     * trigger a refund instead (docs/architecture/payment-flow.md). */
    public boolean confirm(String providerBookingReference, Ticket ticket, Instant now) {
        Objects.requireNonNull(providerBookingReference, "providerBookingReference must not be null");
        Objects.requireNonNull(ticket, "ticket must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (status != BookingStatus.PENDING_PAYMENT) {
            return false;
        }
        this.providerBookingReference = providerBookingReference;
        this.ticket = ticket;
        this.status = BookingStatus.CONFIRMED;
        this.confirmedAt = now;
        return true;
    }

    /** {@code PENDING_PAYMENT -> CANCELLED} or {@code CONFIRMED -> CANCELLED}. Returns
     * {@code false} (no-op) if already {@code CANCELLED} or {@code COMPLETED} (terminal) —
     * {@code cancellationReason} is set exactly once, never overwritten by a later duplicate
     * trigger (docs/services/booking-service/domain-model.md's invariants). */
    public boolean cancel(CancellationReason reason, Instant now) {
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (status != BookingStatus.PENDING_PAYMENT && status != BookingStatus.CONFIRMED) {
            return false;
        }
        this.status = BookingStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = now;
        return true;
    }

    /** {@code CONFIRMED -> COMPLETED}. Returns {@code false} (no-op) if not currently
     * {@code CONFIRMED}. */
    public boolean complete(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != BookingStatus.CONFIRMED) {
            return false;
        }
        this.status = BookingStatus.COMPLETED;
        this.completedAt = now;
        return true;
    }

    /** Marks this booking for human follow-up — set only by the two reconciliation edge cases
     * documented in {@code docs/architecture/booking-flow.md} and
     * {@code docs/architecture/payment-flow.md}: provider confirmation failing after payment
     * already succeeded, and a late payment success after a timeout-driven cancellation. Never
     * cleared automatically — support resolves and clears it operationally, outside this
     * service's own state machine. */
    public void markSupportFlagged() {
        this.supportFlagged = true;
    }

    public void associatePaymentReference(String paymentReference) {
        this.paymentReference = Objects.requireNonNull(paymentReference, "paymentReference must not be null");
    }

    public boolean isOwnedBy(UUID travelerId) {
        return this.travelerId.equals(travelerId);
    }

    public BookingId id() {
        return id;
    }

    public UUID travelerId() {
        return travelerId;
    }

    public TripId tripId() {
        return tripId;
    }

    public Instant tripDepartureTime() {
        return tripDepartureTime;
    }

    public ProviderType providerType() {
        return providerType;
    }

    public String providerTripId() {
        return providerTripId;
    }

    public String providerBlockReference() {
        return providerBlockReference;
    }

    public Instant holdExpiresAt() {
        return holdExpiresAt;
    }

    public Optional<String> providerBookingReference() {
        return Optional.ofNullable(providerBookingReference);
    }

    public List<Passenger> passengers() {
        return passengers;
    }

    public Fare fare() {
        return fare;
    }

    public BookingStatus status() {
        return status;
    }

    public Optional<CancellationReason> cancellationReason() {
        return Optional.ofNullable(cancellationReason);
    }

    public boolean supportFlagged() {
        return supportFlagged;
    }

    public Optional<String> paymentReference() {
        return Optional.ofNullable(paymentReference);
    }

    public Optional<Ticket> ticket() {
        return Optional.ofNullable(ticket);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> confirmedAt() {
        return Optional.ofNullable(confirmedAt);
    }

    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Booking other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
