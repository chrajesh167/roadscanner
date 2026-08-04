package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import java.util.Objects;

/**
 * FlixBus's trip UID: {@code direct:<ride_id>:<from_station_id>:<to_station_id>}.
 *
 * <p>This is FlixBus's own format, defined by the cart API for a direct ride — not a RoadScanner
 * invention. Adopting it as the {@code providerTripId} carried on every {@code ProviderTrip} means
 * the three UUIDs the seat-map and booking calls require travel with the trip automatically, and
 * cannot be lost between a search response and a booking made minutes later against it. The
 * alternative — a bare ride id plus side storage for the two station ids — gives two things that
 * can drift apart, and the drift only shows up at booking time.
 *
 * <p>Opaque to every other layer. Search results carry it as a string; only this package knows it
 * decomposes, and only this package is allowed to.
 */
record FlixBusTripUid(String rideId, String fromStationId, String toStationId) {

    /** The only trip type this integration books; interconnection trips are filtered out earlier. */
    private static final String DIRECT = "direct";
    private static final String SEPARATOR = ":";

    FlixBusTripUid {
        rideId = requireNonBlank(rideId, "rideId");
        fromStationId = requireNonBlank(fromStationId, "fromStationId");
        toStationId = requireNonBlank(toStationId, "toStationId");
    }

    /**
     * Parses a trip UID previously produced by {@link #value()}.
     *
     * @throws IllegalArgumentException if the value is not a direct-trip UID. Deliberately strict:
     *         a malformed UID reaching the cart call is rejected by FlixBus with an error that says
     *         nothing about which of the three ids was wrong.
     */
    static FlixBusTripUid parse(String value) {
        Objects.requireNonNull(value, "trip uid must not be null");
        String[] parts = value.strip().split(SEPARATOR);
        if (parts.length != 4 || !DIRECT.equals(parts[0])) {
            throw new IllegalArgumentException(
                    "Not a FlixBus direct trip uid (expected direct:<rideId>:<fromStationId>:<toStationId>): " + value);
        }
        return new FlixBusTripUid(parts[1], parts[2], parts[3]);
    }

    String value() {
        return DIRECT + SEPARATOR + rideId + SEPARATOR + fromStationId + SEPARATOR + toStationId;
    }

    @Override
    public String toString() {
        return value();
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
