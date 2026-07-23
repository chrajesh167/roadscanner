package com.roadscanner.bookingservice.application.usecase.hold;

import com.roadscanner.bookingservice.domain.exception.SeatHoldNotFoundException;
import com.roadscanner.bookingservice.domain.model.SeatHold;
import com.roadscanner.bookingservice.domain.port.in.ReleaseHold;
import com.roadscanner.bookingservice.domain.port.out.ProviderIntegrationClient;
import com.roadscanner.bookingservice.domain.port.out.SeatHoldRepository;

/** Implements {@link ReleaseHold}. */
public class ReleaseHoldService implements ReleaseHold {

    private final SeatHoldRepository seatHoldRepository;
    private final ProviderIntegrationClient providerIntegrationClient;

    public ReleaseHoldService(SeatHoldRepository seatHoldRepository,
                               ProviderIntegrationClient providerIntegrationClient) {
        this.seatHoldRepository = seatHoldRepository;
        this.providerIntegrationClient = providerIntegrationClient;
    }

    @Override
    public Result release(Command command) {
        SeatHold hold = seatHoldRepository.findById(command.seatHoldId())
                .filter(h -> h.isOwnedBy(command.travelerId()))
                .orElseThrow(() -> new SeatHoldNotFoundException(command.seatHoldId()));

        boolean released = providerIntegrationClient.releaseSeat(hold.providerType(), hold.providerBlockReference());
        seatHoldRepository.deleteById(hold.id());
        return new Result(released);
    }
}
