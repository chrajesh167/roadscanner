package com.roadscanner.providerintegrationservice.application.usecase.booking;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.in.ConfirmBooking;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

/** Implements {@link ConfirmBooking}. */
public class ConfirmBookingService implements ConfirmBooking {

    private final ActiveSessionResolver sessionResolver;
    private final ProviderClientRegistry registry;

    public ConfirmBookingService(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry) {
        this.sessionResolver = sessionResolver;
        this.registry = registry;
    }

    @Override
    public Result confirm(Command command) {
        ProviderSession session = sessionResolver.resolveActive(command.sessionId());
        ProviderClient client = registry.resolveWithCapability(session.providerType(), ProviderCapability.BOOKING_CONFIRMATION);
        BookingConfirmation confirmation = client.confirmBooking(session, command.providerBlockReference(),
                command.providerTripId(), command.passengers());
        return new Result(confirmation);
    }
}
