package com.roadscanner.providerintegrationservice.adapter.in.rest.ticket;

import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.port.in.DownloadTicket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Internal-only — fetches the ticket document for a confirmed booking. */
@RestController
@RequestMapping("/internal/api/v1/providers/{providerType}/sessions/{sessionId}/bookings/{bookingReference}/ticket")
@Tag(name = "Provider Tickets", description = "Download a confirmed booking's ticket")
class ProviderTicketController {

    private final DownloadTicket downloadTicket;

    ProviderTicketController(DownloadTicket downloadTicket) {
        this.downloadTicket = downloadTicket;
    }

    @GetMapping
    @Operation(summary = "Download ticket", description = "Content is base64-encoded in the response body — see the format field for how to interpret it.")
    TicketResponse download(@PathVariable String providerType, @PathVariable UUID sessionId,
                             @PathVariable String bookingReference) {
        DownloadTicket.Result result = downloadTicket.download(new DownloadTicket.Command(
                new ProviderSessionId(sessionId), new BookingReference(bookingReference)));
        return TicketResponse.from(result.ticket());
    }
}
