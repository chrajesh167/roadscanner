package com.roadscanner.inventoryservice.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistence shape for {@code SeatLayout} — one row per trip, its seats held in a child table
 * via {@code @ElementCollection} (they have no independent identity or lifecycle of their own). */
@Entity
@Table(name = "seat_layouts")
public class SeatLayoutJpaEntity {

    @Id
    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "seat_layout_seats", joinColumns = @JoinColumn(name = "trip_id"))
    private List<SeatEmbeddable> seats = new ArrayList<>();

    protected SeatLayoutJpaEntity() {
    }

    public SeatLayoutJpaEntity(UUID tripId, List<SeatEmbeddable> seats) {
        this.tripId = tripId;
        this.seats = new ArrayList<>(seats);
    }

    public UUID getTripId() {
        return tripId;
    }

    public List<SeatEmbeddable> getSeats() {
        return seats;
    }
}
