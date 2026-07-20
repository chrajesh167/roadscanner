package com.roadscanner.providerintegrationservice.adapter.out.provider.mock;

import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.HealthState;
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

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A complete, self-contained provider implementation backed by {@link MockProviderDataStore} —
 * no network calls, no external dependency, behaves like a real provider for every capability
 * (search, seat map, block, release, confirm, ticket, health). This is what makes the rest of
 * the platform end-to-end testable (including {@code ProviderIntegrationServiceEndToEndTest})
 * before FlixBus credentials exist. Registered under {@link ProviderType#MOCK} — see
 * {@code db/migration/V5__seed_provider_configurations.sql} for its (enabled) configuration row.
 */
@Component
class MockProviderClientAdapter implements ProviderClient {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final MockProviderDataStore dataStore;
    private final Clock clock;

    MockProviderClientAdapter(Clock clock) {
        this.clock = clock;
        this.dataStore = new MockProviderDataStore(clock);
    }

    @Override
    public ProviderType supportedType() {
        return ProviderType.MOCK;
    }

    @Override
    public Set<ProviderCapability> supportedCapabilities() {
        return Set.of(ProviderCapability.values());
    }

    @Override
    public ProviderToken authenticate(Provider provider) {
        return issueToken();
    }

    @Override
    public ProviderToken refreshSession(Provider provider, ProviderSession session) {
        return issueToken();
    }

    @Override
    public List<ProviderTrip> search(ProviderSession session, SearchCriteria criteria) {
        return dataStore.search(criteria);
    }

    @Override
    public ProviderSeatMap getSeatMap(ProviderSession session, String providerTripId) {
        return dataStore.getSeatMap(providerTripId);
    }

    @Override
    public SeatReservation blockSeats(ProviderSession session, String providerTripId, List<SeatNumber> seatNumbers) {
        return dataStore.block(providerTripId, seatNumbers);
    }

    @Override
    public void releaseSeats(ProviderSession session, String providerBlockReference) {
        dataStore.release(providerBlockReference);
    }

    @Override
    public BookingConfirmation confirmBooking(ProviderSession session, String providerBlockReference,
                                               String providerTripId, List<PassengerDetail> passengers) {
        return dataStore.confirm(providerBlockReference, providerTripId, passengers);
    }

    @Override
    public ProviderTicket downloadTicket(ProviderSession session, BookingReference bookingReference) {
        return dataStore.downloadTicket(bookingReference);
    }

    @Override
    public ProviderHealthCheck checkHealth(Provider provider) {
        return new ProviderHealthCheck(ProviderType.MOCK, HealthState.HEALTHY, clock.instant(),
                "Mock provider is always healthy");
    }

    private ProviderToken issueToken() {
        return new ProviderToken("mock-access-" + UUID.randomUUID(), "mock-refresh-" + UUID.randomUUID(),
                "Bearer", clock.instant().plus(TOKEN_TTL));
    }
}
