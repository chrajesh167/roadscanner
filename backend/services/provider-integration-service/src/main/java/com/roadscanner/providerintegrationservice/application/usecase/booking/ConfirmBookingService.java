package com.roadscanner.providerintegrationservice.application.usecase.booking;

import com.roadscanner.providerintegrationservice.application.usecase.session.ActiveSessionResolver;
import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.ProviderBooking;
import com.roadscanner.providerintegrationservice.domain.model.ProviderBookingId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.in.ConfirmBooking;
import com.roadscanner.providerintegrationservice.domain.exception.ReservationNotFoundException;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderBookingRepository;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderReservationRepository;
import com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry;

/** Implements {@link ConfirmBooking}. */
public class ConfirmBookingService implements ConfirmBooking {

    private final ActiveSessionResolver sessionResolver;
    private final ProviderClientRegistry registry;
    private final ProviderReservationRepository reservationRepository;
    private final ProviderBookingRepository bookingRepository;

    public ConfirmBookingService(ActiveSessionResolver sessionResolver, ProviderClientRegistry registry,
                                 ProviderReservationRepository reservationRepository,
                                 ProviderBookingRepository bookingRepository) {
        this.sessionResolver = sessionResolver;
        this.registry = registry;
        this.reservationRepository = reservationRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public Result confirm(Command command) {
        ProviderSession session = sessionResolver.resolveActive(command.sessionId());
        ProviderClient client = registry.resolveWithCapability(session.providerType(), ProviderCapability.BOOKING_CONFIRMATION);
        SeatReservation reservation = reservationRepository
                .findByBlockReference(session.providerType(), command.providerBlockReference())
                .orElseThrow(() -> new ReservationNotFoundException(session.providerType(), command.providerBlockReference()));

        BookingConfirmation confirmation = client.confirmBooking(session, reservation, command.contact(),
                command.passengers());

        // The order's handles are persisted before anything else can go wrong. The token
        // authorising cancellation is issued once, with the order, and no provider will reissue
        // it — losing it here converts a paid, cancellable booking into a support ticket.
        bookingRepository.save(ProviderBooking.record(ProviderBookingId.generate(), session.providerType(),
                confirmation));

        // The hold is now spent. Recorded before returning so a repeated confirmation is refused by
        // the aggregate rather than charging a second time.
        reservation.confirm(confirmation.confirmedAt());
        reservationRepository.save(reservation);

        return new Result(confirmation);
    }
}
