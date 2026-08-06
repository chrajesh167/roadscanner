package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The durable record of a confirmed provider order, and the only place its handles survive.
 *
 * <p>{@link BookingConfirmation} carries these handles out of a single {@code confirmBooking}
 * round-trip; nothing kept them afterwards. That made the order token — which authorises reading
 * and cancelling the order — unrecoverable the moment the call returned, so every paid provider
 * order became permanently uncancellable. This aggregate is what closes that gap.
 *
 * <p>The token is deliberately never returned across the service boundary. It is a provider
 * credential, and {@code booking-service} has no use for one: it asks this service to cancel by
 * order reference, and the token is resolved here. Shipping it outward would widen the blast
 * radius of a secret for no caller benefit.
 */
public final class ProviderBooking {

    private final ProviderBookingId id;
    private final ReservationId reservationId;
    private final ProviderType providerType;
    private final BookingReference bookingReference;
    private final String providerCheckoutReference;
    private final String providerOrderReference;
    private final String providerOrderToken;
    private final FareAmount totalFare;
    private final Instant confirmedAt;
    private Instant cancelledAt;
    private java.math.BigDecimal refundedAmount;

    private ProviderBooking(ProviderBookingId id, ReservationId reservationId, ProviderType providerType,
                            BookingReference bookingReference, String providerCheckoutReference,
                            String providerOrderReference, String providerOrderToken, FareAmount totalFare,
                            Instant confirmedAt, Instant cancelledAt, java.math.BigDecimal refundedAmount) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId must not be null");
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        this.bookingReference = Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        if (providerOrderReference == null || providerOrderReference.isBlank()) {
            throw new IllegalArgumentException("providerOrderReference must not be blank");
        }
        this.providerCheckoutReference = providerCheckoutReference;
        this.providerOrderReference = providerOrderReference;
        this.providerOrderToken = providerOrderToken;
        this.totalFare = Objects.requireNonNull(totalFare, "totalFare must not be null");
        this.confirmedAt = Objects.requireNonNull(confirmedAt, "confirmedAt must not be null");
        this.cancelledAt = cancelledAt;
        this.refundedAmount = refundedAmount;
    }

    /** Records what a provider returned when it confirmed the order. */
    public static ProviderBooking record(ProviderBookingId id, ProviderType providerType,
                                         BookingConfirmation confirmation) {
        return new ProviderBooking(id, confirmation.reservationId(), providerType, confirmation.bookingReference(),
                confirmation.providerCheckoutReference(), confirmation.providerOrderReference(),
                confirmation.providerOrderToken(), confirmation.totalFare(), confirmation.confirmedAt(),
                null, null);
    }

    /** Rehydrates from persisted state. Trusts the state is already valid. */
    public static ProviderBooking reconstitute(ProviderBookingId id, ReservationId reservationId,
                                               ProviderType providerType, BookingReference bookingReference,
                                               String providerCheckoutReference, String providerOrderReference,
                                               String providerOrderToken, FareAmount totalFare, Instant confirmedAt,
                                               Instant cancelledAt, java.math.BigDecimal refundedAmount) {
        return new ProviderBooking(id, reservationId, providerType, bookingReference, providerCheckoutReference,
                providerOrderReference, providerOrderToken, totalFare, confirmedAt, cancelledAt, refundedAmount);
    }

    /**
     * Records the provider's own cancellation outcome.
     *
     * <p>Idempotent: cancelling an already-cancelled order keeps the first outcome rather than
     * overwriting it, because the refund the provider actually paid was decided by the first call
     * and a repeat cancellation reports nothing new.
     */
    public void markCancelled(Instant when, java.math.BigDecimal refunded) {
        if (cancelledAt != null) {
            return;
        }
        this.cancelledAt = Objects.requireNonNull(when, "when must not be null");
        this.refundedAmount = refunded;
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    /**
     * The secret authorising reads and cancellation of this order.
     *
     * <p>Absent is a real state — not every provider issues one — and a caller must treat it as
     * "cannot act on this order through the provider" rather than sending a blank token.
     */
    public Optional<String> providerOrderToken() {
        return Optional.ofNullable(providerOrderToken);
    }

    /** Absent for a provider that issues no separate pre-order handle. */
    public Optional<String> providerCheckoutReference() {
        return Optional.ofNullable(providerCheckoutReference);
    }

    public ProviderBookingId id() {
        return id;
    }

    public ReservationId reservationId() {
        return reservationId;
    }

    public ProviderType providerType() {
        return providerType;
    }

    public BookingReference bookingReference() {
        return bookingReference;
    }

    public String providerOrderReference() {
        return providerOrderReference;
    }

    public FareAmount totalFare() {
        return totalFare;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    public Optional<java.math.BigDecimal> refundedAmount() {
        return Optional.ofNullable(refundedAmount);
    }
}
