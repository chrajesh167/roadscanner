package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.CancellationResult;
import com.roadscanner.providerintegrationservice.domain.model.ContactDetail;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealthCheck;
import com.roadscanner.providerintegrationservice.domain.model.ProviderOrder;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTicket;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Implements the {@link ProviderClient} port for FlixBus by composing the per-endpoint clients and
 * the {@link FlixBusBookingOrchestrator}. Contains no HTTP or mapping logic of its own.
 *
 * <p>Every FlixBus-specific detail — paths, wire shapes, header layering, the trip-uid format —
 * stays inside this {@code adapter.out.provider.flixbus} package, per the platform rule that no
 * other package ever hardcodes provider-specific behaviour.
 *
 * <p><strong>Two capabilities are deliberately not declared.</strong> The documented API contains no
 * cart-abandon endpoint and no ticket-download endpoint, so this adapter does not claim
 * {@code SEAT_RELEASE} or {@code TICKET_DOWNLOAD}. Advertising them and then calling an invented URL
 * would turn a clean "not supported" into a failed request against something FlixBus never
 * published. Both methods below refuse explicitly for the same reason.
 */
@Component
class FlixBusProviderClientAdapter implements ProviderClient {

    private static final Logger log = LoggerFactory.getLogger(FlixBusProviderClientAdapter.class);

    private final FlixBusAuthenticationClient authenticationClient;
    private final FlixBusSessionProvider sessionProvider;
    private final FlixBusSearchClient searchClient;
    private final FlixBusSeatMapClient seatMapClient;
    private final FlixBusOrderClient orderClient;
    private final FlixBusBookingOrchestrator orchestrator;
    private final ProviderConfigurationRepository configurationRepository;

    FlixBusProviderClientAdapter(FlixBusAuthenticationClient authenticationClient,
                                 FlixBusSessionProvider sessionProvider, FlixBusSearchClient searchClient,
                                 FlixBusSeatMapClient seatMapClient, FlixBusOrderClient orderClient,
                                 FlixBusBookingOrchestrator orchestrator,
                                 ProviderConfigurationRepository configurationRepository) {
        this.authenticationClient = authenticationClient;
        this.sessionProvider = sessionProvider;
        this.searchClient = searchClient;
        this.seatMapClient = seatMapClient;
        this.orderClient = orderClient;
        this.orchestrator = orchestrator;
        this.configurationRepository = configurationRepository;
    }

    @Override
    public ProviderType supportedType() {
        return ProviderType.FLIXBUS;
    }

    @Override
    public Set<ProviderCapability> supportedCapabilities() {
        return Set.of(ProviderCapability.SEARCH, ProviderCapability.SEAT_MAP, ProviderCapability.SEAT_BLOCK,
                ProviderCapability.BOOKING_CONFIRMATION, ProviderCapability.BOOKING_CANCELLATION,
                ProviderCapability.ORDER_DETAILS, ProviderCapability.HEALTH_CHECK);
    }

    @Override
    public ProviderToken authenticate(Provider provider) {
        return authenticationClient.login(provider);
    }

    /**
     * Renewal is a fresh partner login: the documented API has no refresh endpoint and issues no
     * refresh token. The cached session is discarded first so the new token replaces it rather than
     * living alongside it.
     */
    @Override
    public ProviderToken refreshSession(Provider provider, ProviderSession session) {
        sessionProvider.invalidate(provider);
        return authenticationClient.login(provider);
    }

    @Override
    public List<ProviderTrip> search(Provider provider, SearchCriteria criteria) {
        return searchClient.search(provider, criteria);
    }

    @Override
    public ProviderSeatMap getSeatMap(ProviderSession session, String providerTripId) {
        // Seat prices here are the category surcharge alone. The trip's base fare is not knowable
        // from a seat-map request, and it is already carried on the search result the caller holds —
        // adding a fabricated base would double-count it at booking.
        return seatMapClient.getSeatMap(resolve(), FlixBusTripUid.parse(providerTripId), BigDecimal.ZERO);
    }

    @Override
    public SeatReservation blockSeats(ProviderSession session, String providerTripId,
                                      List<PassengerDetail> passengers) {
        return orchestrator.blockSeats(resolve(), FlixBusTripUid.parse(providerTripId),
                passengers.stream().map(PassengerDetail::seatNumber).toList(), passengers);
    }

    @Override
    public void releaseSeats(ProviderSession session, String providerBlockReference) {
        // The documented API publishes no cart-abandon endpoint. Succeeding silently would tell the
        // caller the seats were freed when nothing was asked of FlixBus at all; inventing a URL
        // would be worse. The cart is left to lapse provider-side and the caller is told plainly.
        log.warn("FlixBus publishes no seat-release endpoint; cart {} will be left to expire", providerBlockReference);
        throw new ProviderNotSupportedException(ProviderType.FLIXBUS);
    }

    @Override
    public BookingConfirmation confirmBooking(ProviderSession session, SeatReservation reservation,
                                              ContactDetail contact, List<PassengerDetail> passengers) {
        return orchestrator.confirmBooking(resolve(), reservation, contact, passengers);
    }

    @Override
    public CancellationResult cancelBooking(ProviderSession session, String providerOrderReference,
                                            String providerOrderToken, String reason) {
        return orderClient.cancelOrder(resolve(), providerOrderReference, providerOrderToken, reason);
    }

    @Override
    public ProviderOrder getOrderDetails(ProviderSession session, String providerOrderReference,
                                         String providerOrderToken) {
        return orderClient.getOrder(resolve(), providerOrderReference, providerOrderToken);
    }

    @Override
    public ProviderTicket downloadTicket(ProviderSession session, BookingReference bookingReference) {
        // No ticket-download endpoint is documented. Order details (endpoint 10) returns the full
        // order, which is what a "view booking" feature should read instead.
        throw new ProviderNotSupportedException(ProviderType.FLIXBUS);
    }

    @Override
    public ProviderHealthCheck checkHealth(Provider provider) {
        return authenticationClient.checkHealth(provider);
    }

    /**
     * The FlixBus registry row.
     *
     * <p>The session-scoped port methods identify a provider only by the session's type, but every
     * FlixBus call needs the registration itself — its base URL, and the credential row keyed to its
     * id. A session for a provider that has since been deregistered is a genuine error rather than
     * something to paper over with a default.
     */
    private Provider resolve() {
        return configurationRepository.findByType(ProviderType.FLIXBUS)
                .orElseThrow(() -> new ProviderNotSupportedException(ProviderType.FLIXBUS));
    }
}
