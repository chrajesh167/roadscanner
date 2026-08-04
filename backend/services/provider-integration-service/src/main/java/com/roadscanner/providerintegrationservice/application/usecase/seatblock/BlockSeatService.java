package com.roadscanner.providerintegrationservice.application.usecase.seatblock;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;
import com.roadscanner.providerintegrationservice.domain.port.in.BlockSeat;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderCache;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderReservationRepository;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

/** Implements {@link BlockSeat}. Evicting the trip's cached seat map isn't done here — a 30s-TTL
 * entry going briefly stale after a block is an accepted trade-off (matching
 * {@code search-service}'s short-TTL availability cache philosophy), not a correctness issue:
 * the next {@code GetSeatMap} call past that TTL sees the true state either way. */
public class BlockSeatService implements BlockSeat {

    private final ActiveSessionResolver sessionResolver;
    private final ProviderClientRegistry registry;
    private final ProviderReservationRepository reservationRepository;

    public BlockSeatService(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry,
                            ProviderReservationRepository reservationRepository) {
        this.sessionResolver = sessionResolver;
        this.registry = registry;
        this.reservationRepository = reservationRepository;
    }

    @Override
    public Result block(Command command) {
        ProviderSession session = sessionResolver.resolveActive(command.sessionId());
        ProviderClient client = registry.resolveWithCapability(session.providerType(), ProviderCapability.SEAT_BLOCK);
        SeatReservation reservation = client.blockSeats(session, command.providerTripId(), command.passengers());

        // Persisted immediately, before the caller is told the hold exists. The provider handles
        // minted during the block are unrecoverable, so a crash between here and the response must
        // not be the difference between a confirmable booking and an orphaned one.
        reservationRepository.save(reservation);
        return new Result(reservation);
    }
}
