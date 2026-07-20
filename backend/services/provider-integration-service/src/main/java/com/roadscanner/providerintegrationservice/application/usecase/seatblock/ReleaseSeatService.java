package com.roadscanner.providerintegrationservice.application.usecase.seatblock;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.in.ReleaseSeat;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

/** Implements {@link ReleaseSeat}. {@code Result.released} reflects that the call completed
 * without error — per {@link ProviderClient#releaseSeats}'s idempotent contract, this is {@code
 * true} whether or not the block was still active when the call arrived. */
public class ReleaseSeatService implements ReleaseSeat {

    private final ActiveSessionResolver sessionResolver;
    private final ProviderClientRegistry registry;

    public ReleaseSeatService(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        this.sessionResolver = sessionResolver;
        this.registry = registry;
    }

    @Override
    public Result release(Command command) {
        ProviderSession session = sessionResolver.resolveActive(command.sessionId());
        ProviderClient client = registry.resolveWithCapability(session.providerType(), ProviderCapability.SEAT_RELEASE);
        client.releaseSeats(session, command.providerBlockReference());
        return new Result(true);
    }
}
