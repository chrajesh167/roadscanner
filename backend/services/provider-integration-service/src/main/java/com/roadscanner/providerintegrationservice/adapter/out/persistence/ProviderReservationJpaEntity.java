package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Row mapping for {@code provider_reservations}. */
@Entity
@Table(name = "provider_reservations")
class ProviderReservationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "provider_type", nullable = false)
    private String providerType;

    @Column(name = "provider_block_reference", nullable = false)
    private String providerBlockReference;

    @Column(name = "provider_trip_id", nullable = false)
    private String providerTripId;

    @Column(nullable = false)
    private String status;

    @Column(name = "blocked_at", nullable = false)
    private Instant blockedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // Cascade + orphan removal so the seat rows are written and replaced with their parent: a
    // reservation without its seat detail is not a reservation anyone can confirm.
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<ProviderReservationSeatJpaEntity> seats = new ArrayList<>();

    protected ProviderReservationJpaEntity() {
    }

    ProviderReservationJpaEntity(UUID id, String providerType, String providerBlockReference, String providerTripId,
                                 String status, Instant blockedAt, Instant expiresAt) {
        this.id = id;
        this.providerType = providerType;
        this.providerBlockReference = providerBlockReference;
        this.providerTripId = providerTripId;
        this.status = status;
        this.blockedAt = blockedAt;
        this.expiresAt = expiresAt;
    }

    void replaceSeats(List<ProviderReservationSeatJpaEntity> replacements) {
        seats.clear();
        replacements.forEach(seat -> {
            seat.attachTo(this);
            seats.add(seat);
        });
    }

    void updateStatus(String newStatus) {
        this.status = newStatus;
    }

    UUID id() {
        return id;
    }

    String providerType() {
        return providerType;
    }

    String providerBlockReference() {
        return providerBlockReference;
    }

    String providerTripId() {
        return providerTripId;
    }

    String status() {
        return status;
    }

    Instant blockedAt() {
        return blockedAt;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    List<ProviderReservationSeatJpaEntity> seats() {
        return seats;
    }
}
