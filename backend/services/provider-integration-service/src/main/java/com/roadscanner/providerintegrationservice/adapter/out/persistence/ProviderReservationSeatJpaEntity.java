package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** Row mapping for {@code provider_reservation_seats}. */
@Entity
@Table(name = "provider_reservation_seats")
class ProviderReservationSeatJpaEntity {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "reservation_id", nullable = false)
    private ProviderReservationJpaEntity reservation;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(name = "provider_seat_id", nullable = false)
    private String providerSeatId;

    @Column(name = "provider_ticket_id", nullable = false)
    private String providerTicketId;

    @Column(name = "position", nullable = false)
    private int position;

    protected ProviderReservationSeatJpaEntity() {
    }

    ProviderReservationSeatJpaEntity(UUID id, String seatNumber, String providerSeatId, String providerTicketId,
                                     int position) {
        this.id = id;
        this.seatNumber = seatNumber;
        this.providerSeatId = providerSeatId;
        this.providerTicketId = providerTicketId;
        this.position = position;
    }

    void attachTo(ProviderReservationJpaEntity parent) {
        this.reservation = parent;
    }

    String seatNumber() {
        return seatNumber;
    }

    String providerSeatId() {
        return providerSeatId;
    }

    String providerTicketId() {
        return providerTicketId;
    }
}
