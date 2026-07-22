package com.roadscanner.inventoryservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One row of {@code seat_layout_seats} — static shape only, no status column exists to add
 * one to (see {@code Seat}'s Javadoc — this is the persistence-side enforcement of that rule). */
@Embeddable
public class SeatEmbeddable {

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(name = "deck", nullable = false)
    private String deck;

    @Column(name = "seat_type", nullable = false)
    private String seatType;

    @Column(name = "wheelchair_accessible", nullable = false)
    private boolean wheelchairAccessible;

    @Column(name = "position")
    private Integer position;

    protected SeatEmbeddable() {
    }

    public SeatEmbeddable(String seatNumber, String deck, String seatType, boolean wheelchairAccessible, Integer position) {
        this.seatNumber = seatNumber;
        this.deck = deck;
        this.seatType = seatType;
        this.wheelchairAccessible = wheelchairAccessible;
        this.position = position;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public String getDeck() {
        return deck;
    }

    public String getSeatType() {
        return seatType;
    }

    public boolean isWheelchairAccessible() {
        return wheelchairAccessible;
    }

    public Integer getPosition() {
        return position;
    }
}
