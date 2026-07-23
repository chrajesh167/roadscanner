package com.roadscanner.bookingservice.adapter.in.rest.ticket;

import com.roadscanner.bookingservice.domain.model.Ticket;

import java.time.Instant;
import java.util.Base64;

public record TicketResponse(String providerTicketId, String format, String contentBase64, Instant issuedAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(ticket.providerTicketId(), ticket.format(),
                Base64.getEncoder().encodeToString(ticket.content()), ticket.issuedAt());
    }
}
