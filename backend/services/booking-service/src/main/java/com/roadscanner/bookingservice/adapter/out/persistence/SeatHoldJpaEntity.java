package com.roadscanner.bookingservice.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistence shape for {@code SeatHold} — always fresh-inserted, never mutated once created
 * (docs/services/booking-service/data-ownership.md: "deliberately transient"), matching
 * {@code inventory-service}'s {@code SeatLayoutJpaEntity} posture for the same reason. */
@Entity
@Table(name = "seat_holds")
public class SeatHoldJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "traveler_id", nullable = false, updatable = false)
    private UUID travelerId;

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "trip_departure_time", nullable = false, updatable = false)
    private Instant tripDepartureTime;

    @Column(name = "provider_type", nullable = false, updatable = false)
    private String providerType;

    @Column(name = "provider_trip_id", nullable = false, updatable = false)
    private String providerTripId;

    @Column(name = "provider_block_reference", nullable = false, updatable = false)
    private String providerBlockReference;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "seat_hold_seat_numbers", joinColumns = @JoinColumn(name = "seat_hold_id"))
    @Column(name = "seat_number", nullable = false)
    private List<String> seatNumbers = new ArrayList<>();

    @Column(name = "fare_amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal fareAmount;

    @Column(name = "fare_currency", nullable = false, updatable = false)
    private String fareCurrency;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SeatHoldJpaEntity() {
    }

    public SeatHoldJpaEntity(UUID id, UUID travelerId, UUID tripId, Instant tripDepartureTime, String providerType,
                              String providerTripId, String providerBlockReference, List<String> seatNumbers,
                              BigDecimal fareAmount, String fareCurrency, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.travelerId = travelerId;
        this.tripId = tripId;
        this.tripDepartureTime = tripDepartureTime;
        this.providerType = providerType;
        this.providerTripId = providerTripId;
        this.providerBlockReference = providerBlockReference;
        this.seatNumbers = new ArrayList<>(seatNumbers);
        this.fareAmount = fareAmount;
        this.fareCurrency = fareCurrency;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTravelerId() {
        return travelerId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public Instant getTripDepartureTime() {
        return tripDepartureTime;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getProviderTripId() {
        return providerTripId;
    }

    public String getProviderBlockReference() {
        return providerBlockReference;
    }

    public List<String> getSeatNumbers() {
        return seatNumbers;
    }

    public BigDecimal getFareAmount() {
        return fareAmount;
    }

    public String getFareCurrency() {
        return fareCurrency;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
