package com.roadscanner.providerintegrationservice.adapter.in.rest.ticket;

import com.roadscanner.providerintegrationservice.domain.model.ProviderTicket;

import java.time.Instant;
import java.util.Base64;

public record TicketResponse(String ticketId, String bookingReference, String format, String contentBase64,
                              Instant issuedAt) {

    public static TicketResponse from(ProviderTicket ticket) {
        return new TicketResponse(ticket.ticketId().value(), ticket.bookingReference().value(), ticket.format().name(),
                Base64.getEncoder().encodeToString(ticket.content()), ticket.issuedAt());
    }
}
