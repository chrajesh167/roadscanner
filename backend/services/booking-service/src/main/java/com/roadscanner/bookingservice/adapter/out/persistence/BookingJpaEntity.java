package com.roadscanner.bookingservice.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistence shape for {@code Booking} — the platform's only source of truth for booking
 * records. {@code Fare} and {@code Ticket} are flattened into plain (nullable, for {@code Ticket})
 * columns, matching {@code inventory-service}'s {@code TripJpaEntity} precedent for value
 * objects; passengers live in a child table via {@code @ElementCollection}, matching
 * {@code inventory-service}'s {@code SeatLayoutJpaEntity}/{@code SeatEmbeddable} precedent.
 * {@code @Version} backs the optimistic locking documented in
 * docs/services/booking-service/domain-model.md's "Concurrency".
 */
@Entity
@Table(name = "bookings")
public class BookingJpaEntity {

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

    @Column(name = "hold_expires_at", nullable = false, updatable = false)
    private Instant holdExpiresAt;

    @Column(name = "provider_booking_reference")
    private String providerBookingReference;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_passengers", joinColumns = @JoinColumn(name = "booking_id"))
    private List<PassengerEmbeddable> passengers = new ArrayList<>();

    @Column(name = "fare_amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal fareAmount;

    @Column(name = "fare_currency", nullable = false, updatable = false)
    private String fareCurrency;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "support_flagged", nullable = false)
    private boolean supportFlagged;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "ticket_provider_ticket_id")
    private String ticketProviderTicketId;

    @Column(name = "ticket_format")
    private String ticketFormat;

    @Lob
    @Column(name = "ticket_content")
    private byte[] ticketContent;

    @Column(name = "ticket_issued_at")
    private Instant ticketIssuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected BookingJpaEntity() {
    }

    BookingJpaEntity(UUID id, UUID travelerId, UUID tripId, Instant tripDepartureTime, String providerType,
                      String providerTripId, String providerBlockReference, Instant holdExpiresAt,
                      String providerBookingReference, List<PassengerEmbeddable> passengers, BigDecimal fareAmount,
                      String fareCurrency, String status, String cancellationReason, boolean supportFlagged,
                      String paymentReference, String ticketProviderTicketId, String ticketFormat,
                      byte[] ticketContent, Instant ticketIssuedAt, Instant createdAt, Instant confirmedAt,
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
        this.passengers = new ArrayList<>(passengers);
        this.fareAmount = fareAmount;
        this.fareCurrency = fareCurrency;
        this.status = status;
        this.cancellationReason = cancellationReason;
        this.supportFlagged = supportFlagged;
        this.paymentReference = paymentReference;
        this.ticketProviderTicketId = ticketProviderTicketId;
        this.ticketFormat = ticketFormat;
        this.ticketContent = ticketContent;
        this.ticketIssuedAt = ticketIssuedAt;
        this.createdAt = createdAt;
        this.confirmedAt = confirmedAt;
        this.cancelledAt = cancelledAt;
        this.completedAt = completedAt;
    }

    void applyMutableState(String providerBookingReference, String status, String cancellationReason,
                            boolean supportFlagged, String paymentReference, String ticketProviderTicketId,
                            String ticketFormat, byte[] ticketContent, Instant ticketIssuedAt, Instant confirmedAt,
                            Instant cancelledAt, Instant completedAt) {
        this.providerBookingReference = providerBookingReference;
        this.status = status;
        this.cancellationReason = cancellationReason;
        this.supportFlagged = supportFlagged;
        this.paymentReference = paymentReference;
        this.ticketProviderTicketId = ticketProviderTicketId;
        this.ticketFormat = ticketFormat;
        this.ticketContent = ticketContent;
        this.ticketIssuedAt = ticketIssuedAt;
        this.confirmedAt = confirmedAt;
        this.cancelledAt = cancelledAt;
        this.completedAt = completedAt;
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

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public String getProviderBookingReference() {
        return providerBookingReference;
    }

    public List<PassengerEmbeddable> getPassengers() {
        return passengers;
    }

    public BigDecimal getFareAmount() {
        return fareAmount;
    }

    public String getFareCurrency() {
        return fareCurrency;
    }

    public String getStatus() {
        return status;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public boolean isSupportFlagged() {
        return supportFlagged;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public String getTicketProviderTicketId() {
        return ticketProviderTicketId;
    }

    public String getTicketFormat() {
        return ticketFormat;
    }

    public byte[] getTicketContent() {
        return ticketContent;
    }

    public Instant getTicketIssuedAt() {
        return ticketIssuedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version;
    }
}
