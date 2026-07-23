package com.roadscanner.bookingservice.adapter.in.rest.ticket;

import com.roadscanner.bookingservice.adapter.in.rest.RequesterContextResolver;
import com.roadscanner.bookingservice.domain.model.BookingId;
import com.roadscanner.bookingservice.domain.model.RequesterContext;
import com.roadscanner.bookingservice.domain.port.in.GetTicket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** {@code Get Ticket} — FR-3.6. Returns the ticket already persisted at confirmation time. */
@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/ticket")
@Tag(name = "Tickets", description = "Download the confirmed e-ticket for a booking")
class TicketController {

    private final GetTicket getTicket;

    TicketController(GetTicket getTicket) {
        this.getTicket = getTicket;
    }

    @GetMapping
    @Operation(summary = "Get ticket", description = "Only available once the booking is CONFIRMED or COMPLETED.")
    TicketResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId) {
        RequesterContext requester = RequesterContextResolver.from(jwt);
        GetTicket.Result result = getTicket.get(new GetTicket.Command(new BookingId(bookingId), requester));
        return TicketResponse.from(result.ticket());
    }
}
