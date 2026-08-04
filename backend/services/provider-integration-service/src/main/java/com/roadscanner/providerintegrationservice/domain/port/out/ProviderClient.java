package com.roadscanner.providerintegrationservice.domain.port.out;

import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.CancellationResult;
import com.roadscanner.providerintegrationservice.domain.model.ContactDetail;
import com.roadscanner.providerintegrationservice.domain.model.ProviderOrder;
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
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;

import java.util.List;
import java.util.Set;

/**
 * The strategy every provider integration implements — one bean per provider
 * ({@code FlixBusProviderClientAdapter}, {@code MockProviderClientAdapter}, and any future
 * provider's own adapter), collected at runtime by
 * {@link com.roadscanner.providerintegrationservice.domain.service.ProviderClientRegistry} via
 * {@link #supportedType()}. This is the one outbound port every application/use-case class calls
 * through — no use case ever imports a provider-specific class.
 *
 * Every method may throw a
 * {@link com.roadscanner.providerintegrationservice.domain.exception.ProviderIntegrationException}
 * subtype; implementations are responsible for translating whatever failure mode their transport
 * produces (an HTTP error, a timeout, a malformed response) into the canonical hierarchy before
 * it leaves the adapter package.
 */
public interface ProviderClient {

    ProviderType supportedType();

    Set<ProviderCapability> supportedCapabilities();

    ProviderToken authenticate(Provider provider);

    ProviderToken refreshSession(Provider provider, ProviderSession session);

    /**
     * Searches the provider. Takes the {@link Provider} registry row rather than a
     * {@link ProviderSession} because whether searching needs an authenticated session is the
     * adapter's business — imposing one here would force a login on providers whose static
     * partner credential is already sufficient. The adapter resolves whatever authentication it
     * actually needs, from {@code provider_credentials} and, where relevant, its own session.
     */
    List<ProviderTrip> search(Provider provider, SearchCriteria criteria);

    ProviderSeatMap getSeatMap(ProviderSession session, String providerTripId);

    /**
     * Holds seats for a named set of travellers.
     *
     * <p>Takes {@link PassengerDetail} rather than bare seat numbers because a hold is per
     * traveller, not per seat: providers bind each seat to the person who will occupy it, and seat
     * maps carry gender restrictions that can only be honoured if the occupant is known. The seat
     * each traveller takes is {@link PassengerDetail#seatNumber()}, so no second list can drift out
     * of step with the first.
     */
    SeatReservation blockSeats(ProviderSession session, String providerTripId, List<PassengerDetail> passengers);

    /** {@code providerBlockReference} is the value {@link SeatReservation#providerBlockReference()}
     * carried — the provider's own handle for the block, which is all a release call needs to
     * identify it. */
    void releaseSeats(ProviderSession session, String providerBlockReference);

    /**
     * Turns a held reservation into a confirmed booking.
     *
     * <p>Takes the {@link SeatReservation} rather than only its reference because a provider's
     * confirmation call needs the per-ticket and per-seat handles minted during the block, and
     * those live on the reservation. Passing the reference alone would force every adapter to
     * re-look-up state it was already handed.
     *
     * <p>{@code contact} is booking-scoped: providers deliver the ticket to one address and phone
     * regardless of how many passengers travel on it.
     */
    BookingConfirmation confirmBooking(ProviderSession session, SeatReservation reservation,
                                        ContactDetail contact, List<PassengerDetail> passengers);

    /**
     * Cancels a confirmed order in full.
     *
     * <p>Takes the order token as well as the reference because providers commonly require it to
     * authorise the cancellation — see {@link BookingConfirmation#providerOrderToken()}.
     *
     * <p>Never retried by the execution policy: a timed-out cancellation may already have refunded,
     * and a second attempt is how a double refund happens.
     */
    CancellationResult cancelBooking(ProviderSession session, String providerOrderReference,
                                      String providerOrderToken, String reason);

    /** The provider's own view of an order, for display and support lookups. */
    ProviderOrder getOrderDetails(ProviderSession session, String providerOrderReference, String providerOrderToken);

    ProviderTicket downloadTicket(ProviderSession session, BookingReference bookingReference);

    ProviderHealthCheck checkHealth(Provider provider);
}
