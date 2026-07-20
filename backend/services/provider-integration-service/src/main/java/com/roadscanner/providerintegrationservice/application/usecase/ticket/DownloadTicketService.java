package com.roadscanner.providerintegrationservice.application.usecase.ticket;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTicket;
import com.roadscanner.providerintegrationservice.domain.port.in.DownloadTicket;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

/** Implements {@link DownloadTicket}. */
public class DownloadTicketService implements DownloadTicket {

    private final ActiveSessionResolver sessionResolver;
    private final ProviderClientRegistry registry;

    public DownloadTicketService(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        this.sessionResolver = sessionResolver;
        this.registry = registry;
    }

    @Override
    public Result download(Command command) {
        ProviderSession session = sessionResolver.resolveActive(command.sessionId());
        ProviderClient client = registry.resolveWithCapability(session.providerType(), ProviderCapability.TICKET_DOWNLOAD);
        ProviderTicket ticket = client.downloadTicket(session, command.bookingReference());
        return new Result(ticket);
    }
}
