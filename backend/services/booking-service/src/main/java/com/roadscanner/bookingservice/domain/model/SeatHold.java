package com.roadscanner.bookingservice.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The local record of a seat hold placed with {@code provider-integration-service}, before any
 * {@link Booking} exists — deliberately a separate, short-lived aggregate, not a pre-status of
 * {@code Booking} (docs/services/booking-service/domain-model.md's "Why This Isn't Itself a
 * `Booking` in a `HOLDING` State").
 *
 * <p>Becomes at most one {@link Booking} ({@code Create Booking}), or is discarded (explicit
 * release, TTL expiry via {@code SeatReleased}, or a stale-hold sweep) — never both
 * (docs/services/booking-service/booking-state-machine.md's "Hold Token Becomes At Most One
 * Booking").
 */
public final class SeatHold {

    private final SeatHoldId id;
    private final UUID travelerId;
    private final TripId tripId;
    private final Instant tripDepartureTime;
    private final ProviderType providerType;
    private final String providerTripId;
    private final String providerBlockReference;
    private final List<String> seatNumbers;
    private final Fare fare;
    private final Instant expiresAt;
    private final Instant createdAt;

    private SeatHold(SeatHoldId id, UUID travelerId, TripId tripId, Instant tripDepartureTime,
                      ProviderType providerType, String providerTripId, String providerBlockReference,
                      List<String> seatNumbers, Fare fare, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.travelerId = travelerId;
        this.tripId = tripId;
        this.tripDepartureTime = tripDepartureTime;
        this.providerType = providerType;
        this.providerTripId = providerTripId;
        this.providerBlockReference = providerBlockReference;
        this.seatNumbers = List.copyOf(seatNumbers);
        this.fare = fare;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static SeatHold create(SeatHoldId id, UUID travelerId, TripId tripId, Instant tripDepartureTime,
                                   ProviderType providerType, String providerTripId, String providerBlockReference,
                                   List<String> seatNumbers, Fare fare, Instant expiresAt, Instant now) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(travelerId, "travelerId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(tripDepartureTime, "tripDepartureTime must not be null");
        Objects.requireNonNull(providerType, "providerType must not be null");
        Objects.requireNonNull(providerTripId, "providerTripId must not be null");
        Objects.requireNonNull(providerBlockReference, "providerBlockReference must not be null");
        Objects.requireNonNull(fare, "fare must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            throw new IllegalArgumentException("seatNumbers must not be empty");
        }
        return new SeatHold(id, travelerId, tripId, tripDepartureTime, providerType, providerTripId,
                providerBlockReference, seatNumbers, fare, expiresAt, now);
    }

    public static SeatHold reconstitute(SeatHoldId id, UUID travelerId, TripId tripId, Instant tripDepartureTime,
                                         ProviderType providerType, String providerTripId,
                                         String providerBlockReference, List<String> seatNumbers, Fare fare,
                                         Instant expiresAt, Instant createdAt) {
        return new SeatHold(id, travelerId, tripId, tripDepartureTime, providerType, providerTripId,
                providerBlockReference, seatNumbers, fare, expiresAt, createdAt);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isOwnedBy(UUID travelerId) {
        return this.travelerId.equals(travelerId);
    }

    public SeatHoldId id() {
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

    public Fare fare() {
        return fare;
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

    public List<String> seatNumbers() {
        return seatNumbers;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SeatHold other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
