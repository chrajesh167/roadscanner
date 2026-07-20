package com.roadscanner.providerintegrationservice.domain.port.out;

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

    List<ProviderTrip> search(ProviderSession session, SearchCriteria criteria);

    ProviderSeatMap getSeatMap(ProviderSession session, String providerTripId);

    SeatReservation blockSeats(ProviderSession session, String providerTripId, List<SeatNumber> seatNumbers);

    /** {@code providerBlockReference} is the value {@link SeatReservation#providerBlockReference()}
     * carried — the provider's own handle for the block, which is all a release call needs to
     * identify it. */
    void releaseSeats(ProviderSession session, String providerBlockReference);

    BookingConfirmation confirmBooking(ProviderSession session, String providerBlockReference, String providerTripId,
                                        List<PassengerDetail> passengers);

    ProviderTicket downloadTicket(ProviderSession session, BookingReference bookingReference);

    ProviderHealthCheck checkHealth(Provider provider);
}
