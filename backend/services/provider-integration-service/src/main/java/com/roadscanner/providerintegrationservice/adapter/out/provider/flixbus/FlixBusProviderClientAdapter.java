package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealthCheck;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTicket;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Implements the {@link ProviderClient} port for FlixBus by delegating to five focused
 * collaborators, each owning exactly one concern (authentication, search, seats, booking,
 * ticketing) — this class contains no HTTP or mapping logic of its own, only composition. All
 * FlixBus-specific logic (request/response shapes, error translation, resilience config) is
 * isolated to this {@code adapter.out.provider.flixbus} package, per the platform rule that no
 * other package ever hardcodes provider-specific behavior.
 */
@Component
class FlixBusProviderClientAdapter implements ProviderClient {

    private final FlixBusAuthenticationClient authenticationClient;
    private final FlixBusSearchClient searchClient;
    private final FlixBusSeatClient seatClient;
    private final FlixBusBookingClient bookingClient;
    private final FlixBusTicketClient ticketClient;

    FlixBusProviderClientAdapter(FlixBusAuthenticationClient authenticationClient, FlixBusSearchClient searchClient,
                                  FlixBusSeatClient seatClient, FlixBusBookingClient bookingClient,
                                  FlixBusTicketClient ticketClient) {
        this.authenticationClient = authenticationClient;
        this.searchClient = searchClient;
        this.seatClient = seatClient;
        this.bookingClient = bookingClient;
        this.ticketClient = ticketClient;
    }

    @Override
    public ProviderType supportedType() {
        return ProviderType.FLIXBUS;
    }

    @Override
    public Set<ProviderCapability> supportedCapabilities() {
        return Set.of(ProviderCapability.values());
    }

    @Override
    public ProviderToken authenticate(Provider provider) {
        return authenticationClient.authenticate(provider);
    }

    @Override
    public ProviderToken refreshSession(Provider provider, ProviderSession session) {
        return authenticationClient.refresh(provider, session);
    }

    @Override
    public List<ProviderTrip> search(ProviderSession session, SearchCriteria criteria) {
        return searchClient.search(session, criteria);
    }

    @Override
    public ProviderSeatMap getSeatMap(ProviderSession session, String providerTripId) {
        return seatClient.getSeatMap(session, providerTripId);
    }

    @Override
    public SeatReservation blockSeats(ProviderSession session, String providerTripId, List<SeatNumber> seatNumbers) {
        return seatClient.blockSeats(session, providerTripId, seatNumbers);
    }

    @Override
    public void releaseSeats(ProviderSession session, String providerBlockReference) {
        seatClient.releaseSeats(session, providerBlockReference);
    }

    @Override
    public BookingConfirmation confirmBooking(ProviderSession session, String providerBlockReference,
                                               String providerTripId, List<PassengerDetail> passengers) {
        return bookingClient.confirmBooking(session, providerBlockReference, providerTripId, passengers);
    }

    @Override
    public ProviderTicket downloadTicket(ProviderSession session, BookingReference bookingReference) {
        return ticketClient.downloadTicket(session, bookingReference);
    }

    @Override
    public ProviderHealthCheck checkHealth(Provider provider) {
        return authenticationClient.checkHealth();
    }
}
