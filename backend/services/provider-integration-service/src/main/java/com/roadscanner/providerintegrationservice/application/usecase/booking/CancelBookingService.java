package com.roadscanner.providerintegrationservice.application.usecase.booking;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.model.CancellationResult;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.in.CancelBooking;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

/**
 * Cancels an order with the provider that issued it.
 *
 * <p>Resolved through {@link ProviderCapability#BOOKING_CANCELLATION} so a provider whose API has
 * no cancellation endpoint is refused here, cleanly, rather than reaching an adapter that would
 * have to invent one.
 */
public class CancelBookingService implements CancelBooking {

    private final ActiveSessionResolver sessionResolver;
    private final ProviderClientRegistry registry;

    public CancelBookingService(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        this.sessionResolver = sessionResolver;
        this.registry = registry;
    }

    @Override
    public Result cancel(Command command) {
        ProviderSession session = sessionResolver.resolveActive(command.sessionId());
        ProviderClient client = registry.resolveWithCapability(session.providerType(),
                ProviderCapability.BOOKING_CANCELLATION);

        CancellationResult cancellation = client.cancelBooking(session, command.providerOrderReference(),
                command.providerOrderToken(), command.reason());
        return new Result(cancellation);
    }
}
