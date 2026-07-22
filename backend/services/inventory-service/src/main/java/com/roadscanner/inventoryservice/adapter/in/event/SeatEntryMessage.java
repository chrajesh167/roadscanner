package com.roadscanner.inventoryservice.adapter.in.event;

/** One static seat entry, as {@code operator-service} denormalizes it into {@code TripPublished}
 * — no status field, matching {@code Seat}'s Javadoc
 * (docs/services/inventory-service/boundaries.md's "how a trip instance gets its seat map"). */
public record SeatEntryMessage(String seatNumber, String deck, String seatType, boolean wheelchairAccessible, Integer position) {
}
