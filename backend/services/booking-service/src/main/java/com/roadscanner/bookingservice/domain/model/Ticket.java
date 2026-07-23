package com.roadscanner.bookingservice.domain.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Deliberately field-compatible with {@code provider-integration-service}'s
 * {@code ProviderTicket}/{@code TicketResponse} shape. Persisted here in full at confirmation
 * time — {@code provider-integration-service} does not persist tickets past a single round-trip
 * (docs/services/provider-integration-service/boundaries.md), so this is the only durable copy
 * on the platform, backing FR-3.6's repeatable ticket download
 * (docs/services/booking-service/data-ownership.md).
 */
public record Ticket(String providerTicketId, String format, byte[] content, Instant issuedAt) {

    public Ticket {
        Objects.requireNonNull(providerTicketId, "providerTicketId must not be null");
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ticket other)) {
            return false;
        }
        return providerTicketId.equals(other.providerTicketId) && format.equals(other.format)
                && Arrays.equals(content, other.content) && issuedAt.equals(other.issuedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerTicketId, format, Arrays.hashCode(content), issuedAt);
    }
}
