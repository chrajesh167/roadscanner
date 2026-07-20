package com.roadscanner.providerintegrationservice.testsupport.fakes;

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

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** A fully-configurable {@link ProviderClient} test double — every method returns a caller-supplied
 * value (or throws, via a {@link Supplier} that throws) and records how many times it was called,
 * for application-layer use case tests that must not depend on the real Mock/FlixBus adapters
 * (both package-private, by design — see their Javadoc). */
public final class StubProviderClient implements ProviderClient {

    private final ProviderType type;
    private final Set<ProviderCapability> capabilities;

    public Supplier<ProviderToken> authenticateResult = () -> {
        throw new UnsupportedOperationException();
    };
    public Supplier<ProviderToken> refreshResult = () -> {
        throw new UnsupportedOperationException();
    };
    public Supplier<List<ProviderTrip>> searchResult = List::of;
    public Supplier<ProviderSeatMap> seatMapResult = () -> {
        throw new UnsupportedOperationException();
    };
    public Supplier<SeatReservation> blockResult = () -> {
        throw new UnsupportedOperationException();
    };
    public Runnable releaseAction = () -> {
    };
    public Supplier<BookingConfirmation> confirmResult = () -> {
        throw new UnsupportedOperationException();
    };
    public Supplier<ProviderTicket> ticketResult = () -> {
        throw new UnsupportedOperationException();
    };
    public Supplier<ProviderHealthCheck> healthResult = () -> {
        throw new UnsupportedOperationException();
    };

    public int searchCallCount;
    public int seatMapCallCount;

    public StubProviderClient(ProviderType type, Set<ProviderCapability> capabilities) {
        this.type = type;
        this.capabilities = capabilities;
    }

    @Override
    public ProviderType supportedType() {
        return type;
    }

    @Override
    public Set<ProviderCapability> supportedCapabilities() {
        return capabilities;
    }

    @Override
    public ProviderToken authenticate(Provider provider) {
        return authenticateResult.get();
    }

    @Override
    public ProviderToken refreshSession(Provider provider, ProviderSession session) {
        return refreshResult.get();
    }

    @Override
    public List<ProviderTrip> search(ProviderSession session, SearchCriteria criteria) {
        searchCallCount++;
        return searchResult.get();
    }

    @Override
    public ProviderSeatMap getSeatMap(ProviderSession session, String providerTripId) {
        seatMapCallCount++;
        return seatMapResult.get();
    }

    @Override
    public SeatReservation blockSeats(ProviderSession session, String providerTripId, List<SeatNumber> seatNumbers) {
        return blockResult.get();
    }

    @Override
    public void releaseSeats(ProviderSession session, String providerBlockReference) {
        releaseAction.run();
    }

    @Override
    public BookingConfirmation confirmBooking(ProviderSession session, String providerBlockReference,
                                               String providerTripId, List<PassengerDetail> passengers) {
        return confirmResult.get();
    }

    @Override
    public ProviderTicket downloadTicket(ProviderSession session, BookingReference bookingReference) {
        return ticketResult.get();
    }

    @Override
    public ProviderHealthCheck checkHealth(Provider provider) {
        return healthResult.get();
    }
}
