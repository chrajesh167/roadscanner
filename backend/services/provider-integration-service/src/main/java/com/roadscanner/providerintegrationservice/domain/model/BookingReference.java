package com.roadscanner.providerintegrationservice.domain.model;

/** The provider's own confirmation reference (e.g. a FlixBus PNR) — opaque to this service,
 * passed straight through to the caller and back on {@code DownloadTicket}. Not a UUID, since
 * providers mint these in their own format. */
public record BookingReference(String value) {

    public BookingReference {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
