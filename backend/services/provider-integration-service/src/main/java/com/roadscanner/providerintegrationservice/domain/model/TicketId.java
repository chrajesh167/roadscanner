package com.roadscanner.providerintegrationservice.domain.model;

/** The provider's own ticket/PNR identifier. Like {@link BookingReference}, this is the
 * provider's format, not one this service mints. */
public record TicketId(String value) {

    public TicketId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
