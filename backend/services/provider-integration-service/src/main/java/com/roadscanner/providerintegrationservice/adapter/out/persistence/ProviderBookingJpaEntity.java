package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Row mapping for {@code provider_bookings} (Flyway {@code V7}). */
@Entity
@Table(name = "provider_bookings")
class ProviderBookingJpaEntity {

    @Id
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "provider_type", nullable = false)
    private String providerType;

    @Column(name = "booking_reference", nullable = false)
    private String bookingReference;

    @Column(name = "provider_checkout_reference")
    private String providerCheckoutReference;

    @Column(name = "provider_order_reference", nullable = false)
    private String providerOrderReference;

    @Column(name = "provider_order_token")
    private String providerOrderToken;

    @Column(name = "total_fare_amount", nullable = false)
    private BigDecimal totalFareAmount;

    // V7 declared this CHAR(3) where the rest of the platform uses VARCHAR(3). The column is
    // matched as written rather than migrated: an ISO currency code is always exactly three
    // characters, so the blank-padding that makes CHAR risky elsewhere cannot occur here.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "total_fare_currency", nullable = false, length = 3)
    private String totalFareCurrency;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "refunded_amount")
    private BigDecimal refundedAmount;

    protected ProviderBookingJpaEntity() {
    }

    ProviderBookingJpaEntity(UUID id, UUID reservationId, String providerType, String bookingReference,
                             String providerCheckoutReference, String providerOrderReference,
                             String providerOrderToken, BigDecimal totalFareAmount, String totalFareCurrency,
                             Instant confirmedAt, Instant cancelledAt, BigDecimal refundedAmount) {
        this.id = id;
        this.reservationId = reservationId;
        this.providerType = providerType;
        this.bookingReference = bookingReference;
        this.providerCheckoutReference = providerCheckoutReference;
        this.providerOrderReference = providerOrderReference;
        this.providerOrderToken = providerOrderToken;
        this.totalFareAmount = totalFareAmount;
        this.totalFareCurrency = totalFareCurrency;
        this.confirmedAt = confirmedAt;
        this.cancelledAt = cancelledAt;
        this.refundedAmount = refundedAmount;
    }

    /** The only mutable state: an order is confirmed once and cancelled at most once. */
    void recordCancellation(Instant when, BigDecimal refunded) {
        this.cancelledAt = when;
        this.refundedAmount = refunded;
    }

    UUID id() {
        return id;
    }

    UUID reservationId() {
        return reservationId;
    }

    String providerType() {
        return providerType;
    }

    String bookingReference() {
        return bookingReference;
    }

    String providerCheckoutReference() {
        return providerCheckoutReference;
    }

    String providerOrderReference() {
        return providerOrderReference;
    }

    String providerOrderToken() {
        return providerOrderToken;
    }

    BigDecimal totalFareAmount() {
        return totalFareAmount;
    }

    String totalFareCurrency() {
        return totalFareCurrency;
    }

    Instant confirmedAt() {
        return confirmedAt;
    }

    Instant cancelledAt() {
        return cancelledAt;
    }

    BigDecimal refundedAmount() {
        return refundedAmount;
    }
}
