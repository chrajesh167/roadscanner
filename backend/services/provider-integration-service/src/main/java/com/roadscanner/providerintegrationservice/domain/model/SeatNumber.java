package com.roadscanner.providerintegrationservice.domain.model;

/** A seat identifier as the provider labels it (e.g. {@code "U12"}, {@code "L4"}) — opaque to
 * this service, round-tripped exactly as received from {@link ProviderSeatMap} into a block
 * request. */
public record SeatNumber(String value) {

    public SeatNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
