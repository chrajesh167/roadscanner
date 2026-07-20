package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/** The result of a {@code DownloadTicket} call. {@code content} is the raw ticket payload
 * (PDF bytes, base64-encoded — {@code format} tells the caller how to interpret it); this service
 * never stores it, it's fetched fresh from the provider on every call. */
public record ProviderTicket(TicketId ticketId, BookingReference bookingReference, TicketFormat format,
                              byte[] content, Instant issuedAt) {

    public ProviderTicket {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        Objects.requireNonNull(format, "format must not be null");
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }
        content = content.clone();
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
