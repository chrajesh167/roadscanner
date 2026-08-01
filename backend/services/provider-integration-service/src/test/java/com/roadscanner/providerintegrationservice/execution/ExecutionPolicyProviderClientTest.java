package com.roadscanner.providerintegrationservice.execution;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderNotSupportedException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderUnavailableException;
import com.roadscanner.providerintegrationservice.domain.model.BookingConfirmation;
import com.roadscanner.providerintegrationservice.domain.model.BookingReference;
import com.roadscanner.providerintegrationservice.domain.model.PassengerDetail;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderHealthCheck;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSeatMap;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTicket;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderTrip;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SearchCriteria;
import com.roadscanner.providerintegrationservice.domain.model.SeatNumber;
import com.roadscanner.providerintegrationservice.domain.model.SeatReservation;
import com.roadscanner.providerintegrationservice.domain.model.SessionStatus;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderClient;
import com.roadscanner.providerintegrationservice.testsupport.fakes.InMemoryProviderConfigurationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The decorator's one job that carries real risk: deciding which operations may be repeated.
 *
 * <p>Retrying a timed-out booking confirmation double-books a traveller; retrying a timed-out seat
 * block holds seats twice. That split is encoded once, here, so these tests are what stop a future
 * edit from quietly making a write operation retryable.
 */
class ExecutionPolicyProviderClientTest {

    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

    private ExecutorService pool;
    private InMemoryProviderConfigurationRepository providers;
    private CountingProviderClient delegate;
    private ProviderClient client;

    /** Fails every call with a retryable error, counting attempts. */
    private static final class CountingProviderClient implements ProviderClient {
        private final AtomicInteger calls = new AtomicInteger();

        private RuntimeException retryableFailure() {
            calls.incrementAndGet();
            return new ProviderUnavailableException("down",
                    new ProviderError(ProviderType.MOCK, "PROVIDER_UNAVAILABLE", "down", true));
        }

        int calls() {
            return calls.get();
        }

        @Override
        public ProviderType supportedType() {
            return ProviderType.MOCK;
        }

        @Override
        public Set<ProviderCapability> supportedCapabilities() {
            return Set.of(ProviderCapability.SEARCH);
        }

        @Override
        public ProviderToken authenticate(Provider provider) {
            throw retryableFailure();
        }

        @Override
        public ProviderToken refreshSession(Provider provider, ProviderSession session) {
            throw retryableFailure();
        }

        @Override
        public List<ProviderTrip> search(Provider provider, SearchCriteria criteria) {
            throw retryableFailure();
        }

        @Override
        public ProviderSeatMap getSeatMap(ProviderSession session, String providerTripId) {
            throw retryableFailure();
        }

        @Override
        public SeatReservation blockSeats(ProviderSession session, String providerTripId,
                                          List<SeatNumber> seatNumbers) {
            throw retryableFailure();
        }

        @Override
        public void releaseSeats(ProviderSession session, String providerBlockReference) {
            throw retryableFailure();
        }

        @Override
        public BookingConfirmation confirmBooking(ProviderSession session, String providerBlockReference,
                                                  String providerTripId, List<PassengerDetail> passengers) {
            throw retryableFailure();
        }

        @Override
        public ProviderTicket downloadTicket(ProviderSession session, BookingReference bookingReference) {
            throw retryableFailure();
        }

        @Override
        public ProviderHealthCheck checkHealth(Provider provider) {
            throw retryableFailure();
        }
    }

    private static Provider mockProvider() {
        return Provider.reconstitute(ProviderId.generate(), ProviderType.MOCK, ProviderCategory.BUS, "Mock", true,
                Set.of(ProviderCapability.SEARCH), null, 2_000, 2, NOW, NOW);
    }

    private static ProviderSession session() {
        return ProviderSession.reconstitute(ProviderSessionId.generate(), ProviderType.MOCK, SessionStatus.ACTIVE,
                new ProviderToken("token", null, "Bearer", NOW.plusSeconds(3600)), NOW, NOW);
    }

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(4);
        providers = new InMemoryProviderConfigurationRepository();
        providers.add(mockProvider());
        delegate = new CountingProviderClient();
        client = new ExecutionPolicyProviderClient(delegate, providers,
                new ProviderExecutionExecutor(pool, new ProviderMetrics(new SimpleMeterRegistry())),
                BackoffStrategy.none());
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    void passesThroughIdentityWithoutTouchingTheDelegate() {
        assertThat(client.supportedType()).isEqualTo(ProviderType.MOCK);
        assertThat(client.supportedCapabilities()).containsExactly(ProviderCapability.SEARCH);
        assertThat(delegate.calls()).isZero();
    }

    @Test
    void retriesSearchUsingTheProvidersConfiguredRetryCount() {
        assertThatThrownBy(() -> client.search(mockProvider(), null))
                .isInstanceOf(ProviderUnavailableException.class);

        // retry_count 2 on the row means three attempts.
        assertThat(delegate.calls()).isEqualTo(3);
    }

    @Test
    void retriesTheOtherReadOperations() {
        assertThatThrownBy(() -> client.getSeatMap(session(), "trip-1")).isInstanceOf(RuntimeException.class);
        assertThat(delegate.calls()).isEqualTo(3);
    }

    @Test
    void retriesAuthentication() {
        assertThatThrownBy(() -> client.authenticate(mockProvider())).isInstanceOf(RuntimeException.class);
        assertThat(delegate.calls()).isEqualTo(3);
    }

    @Test
    void neverRetriesBookingConfirmation() {
        assertThatThrownBy(() -> client.confirmBooking(session(), "block-1", "trip-1", List.of()))
                .isInstanceOf(ProviderUnavailableException.class);

        // A timed-out confirmation may already have booked. Retrying is how you double-book.
        assertThat(delegate.calls()).isEqualTo(1);
    }

    @Test
    void neverRetriesSeatBlocking() {
        assertThatThrownBy(() -> client.blockSeats(session(), "trip-1", List.of()))
                .isInstanceOf(ProviderUnavailableException.class);

        assertThat(delegate.calls()).isEqualTo(1);
    }

    @Test
    void neverRetriesSeatRelease() {
        assertThatThrownBy(() -> client.releaseSeats(session(), "block-1"))
                .isInstanceOf(ProviderUnavailableException.class);

        assertThat(delegate.calls()).isEqualTo(1);
    }

    @Test
    void neverRetriesAHealthProbe() {
        assertThatThrownBy(() -> client.checkHealth(mockProvider())).isInstanceOf(RuntimeException.class);

        // A probe that silently retries reports a provider healthy when it needed three goes —
        // exactly the state the probe exists to surface.
        assertThat(delegate.calls()).isEqualTo(1);
    }

    @Test
    void honoursAProviderConfiguredWithZeroRetries() {
        // The policy reads retry_count off the row it is handed, so a provider configured with
        // zero retries gets exactly one attempt — no lookup, no default.
        Provider noRetries = Provider.reconstitute(ProviderId.generate(), ProviderType.MOCK, ProviderCategory.BUS,
                "Mock", true, Set.of(ProviderCapability.SEARCH), null, 2_000, 0, NOW, NOW);

        assertThatThrownBy(() -> client.search(noRetries, null)).isInstanceOf(RuntimeException.class);

        assertThat(delegate.calls()).isEqualTo(1);
    }

    @Test
    void refusesToCallAProviderMissingFromTheRegistry() {
        InMemoryProviderConfigurationRepository empty = new InMemoryProviderConfigurationRepository();
        ProviderClient orphaned = new ExecutionPolicyProviderClient(delegate, empty,
                new ProviderExecutionExecutor(pool, new ProviderMetrics(new SimpleMeterRegistry())),
                BackoffStrategy.none());

        // Falling back to a default timeout would hide a misconfiguration behind timings nobody
        // chose; a session for a deregistered provider is a genuine error. Asserted on a
        // session-scoped operation, since search now receives the resolved row from its caller
        // and so has nothing left to look up.
        assertThatThrownBy(() -> orphaned.getSeatMap(session(), "trip-1"))
                .isInstanceOf(ProviderNotSupportedException.class);
        assertThat(delegate.calls()).isZero();
    }
}
