package com.roadscanner.providerintegrationservice.application.usecase.booking;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderBookingNotFoundException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.model.CancellationResult;
import com.roadscanner.providerintegrationservice.domain.model.ProviderBooking;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.in.CancelBooking;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderBookingRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

import java.time.Clock;

/**
 * Cancels an order with the provider that issued it.
 *
 * <p>Resolved through {@link ProviderCapability#BOOKING_CANCELLATION} so a provider whose API has
 * no cancellation endpoint is refused here, cleanly, rather than reaching an adapter that would
 * have to invent one.
 *
 * <p>The authorising token comes from the {@link ProviderBooking} recorded at confirmation, not
 * from the caller. That is what makes this use case reachable at all: the token is issued once,
 * with the order, no provider reissues it, and no caller outside this service ever holds one.
 */
public class CancelBookingService implements CancelBooking {

    private final ActiveSessionResolver sessionResolver;
    private final ProviderClientRegistry registry;
    private final ProviderBookingRepository bookingRepository;
    private final Clock clock;

    public CancelBookingService(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry,
                                ProviderBookingRepository bookingRepository, Clock clock) {
        this.sessionResolver = sessionResolver;
        this.registry = registry;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    @Override
    public Result cancel(Command command) {
        ProviderSession session = sessionResolver.resolveActive(command.sessionId());
        ProviderClient client = registry.resolveWithCapability(session.providerType(),
                ProviderCapability.BOOKING_CANCELLATION);

        ProviderBooking booking = bookingRepository
                .findByOrderReference(session.providerType(), command.providerOrderReference())
                .orElseThrow(() -> new ProviderBookingNotFoundException(session.providerType(),
                        command.providerOrderReference()));

        // A provider that issued no token cannot be asked to cancel. Saying so plainly beats
        // sending a blank one and reading the provider's rejection as a transport failure.
        String token = booking.providerOrderToken()
                .orElseThrow(() -> new ProviderNotSupportedException(session.providerType()));

        CancellationResult cancellation = client.cancelBooking(session, booking.providerOrderReference(),
                token, command.reason());

        booking.markCancelled(clock.instant(), cancellation.refundedAmount().amount());
        bookingRepository.save(booking);

        return new Result(cancellation);
    }
}
