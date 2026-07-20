package com.roadscanner.providerintegrationservice.domain.service;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Covers the mechanism behind "add a provider without changing business logic" — see
 * {@link ProviderClientRegistry}'s own Javadoc. */
class ProviderClientRegistryTest {

    @Test
    void resolvesByDeclaredProviderType() {
        StubProviderClient mock = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.SEARCH));
        ProviderClientRegistry registry = new ProviderClientRegistry(List.of(mock));

        assertThat(registry.resolve(ProviderType.MOCK)).isSameAs(mock);
        assertThat(registry.isRegistered(ProviderType.MOCK)).isTrue();
        assertThat(registry.isRegistered(ProviderType.FLIXBUS)).isFalse();
    }

    @Test
    void throwsWhenNoAdapterIsRegisteredForTheType() {
        ProviderClientRegistry registry = new ProviderClientRegistry(List.of());

        assertThatThrownBy(() -> registry.resolve(ProviderType.FLIXBUS))
                .isInstanceOf(ProviderNotSupportedException.class);
    }

    @Test
    void resolveWithCapabilityRejectsAnAdapterThatDoesNotSupportIt() {
        StubProviderClient mock = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.SEARCH));
        ProviderClientRegistry registry = new ProviderClientRegistry(List.of(mock));

        assertThatThrownBy(() -> registry.resolveWithCapability(ProviderType.MOCK, ProviderCapability.TICKET_DOWNLOAD))
                .isInstanceOf(ProviderNotSupportedException.class);
    }

    @Test
    void rejectsTwoAdaptersDeclaringTheSameProviderType() {
        StubProviderClient first = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.SEARCH));
        StubProviderClient second = new StubProviderClient(ProviderType.MOCK, Set.of(ProviderCapability.SEARCH));

        assertThatThrownBy(() -> new ProviderClientRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Minimal stub — every method beyond {@code supportedType}/{@code supportedCapabilities} is
     * unused by this test and simply unimplemented. */
    private record StubProviderClient(ProviderType supportedType, Set<ProviderCapability> supportedCapabilities)
            implements ProviderClient {

        @Override
        public ProviderToken authenticate(Provider provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProviderToken refreshSession(Provider provider, ProviderSession session) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ProviderTrip> search(ProviderSession session, SearchCriteria criteria) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProviderSeatMap getSeatMap(ProviderSession session, String providerTripId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SeatReservation blockSeats(ProviderSession session, String providerTripId, List<SeatNumber> seatNumbers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseSeats(ProviderSession session, String providerBlockReference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BookingConfirmation confirmBooking(ProviderSession session, String providerBlockReference,
                                                    String providerTripId, List<PassengerDetail> passengers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProviderTicket downloadTicket(ProviderSession session, BookingReference bookingReference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProviderHealthCheck checkHealth(Provider provider) {
            throw new UnsupportedOperationException();
        }
    }
}
