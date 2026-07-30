package com.roadscanner.providerintegrationservice.execution;

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
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderConfigurationRepository;

import java.util.List;
import java.util.Set;

/**
 * Decorates any {@link ProviderClient} with the shared execution policy, so timeout, retries,
 * backoff, metrics and correlation-id propagation apply identically to every provider without a
 * single adapter implementing them.
 *
 * <p>A decorator rather than a base class on purpose: an adapter cannot opt out of the policy by
 * forgetting to call {@code super}, and a new provider gets the behaviour by existing rather than
 * by remembering to ask for it. It is also what lets the policy read {@code timeout_ms} and
 * {@code retry_count} from the provider row per call — a base class would have to be handed them.
 *
 * <p><strong>Idempotency is encoded here, deliberately.</strong> Seat blocking, seat release and
 * booking confirmation run single-attempt: a timeout on those is inconclusive, so a retry risks a
 * second hold or a duplicate booking. Reads — search, seat map, ticket download, authentication —
 * retry. Getting that split wrong is the difference between resilience and double-booking a
 * traveller, so it lives in one reviewable place instead of being re-decided per adapter.
 *
 * <p>Health checks run single-attempt too. A probe that quietly retries reports a provider as
 * healthy when it needed three goes, which is precisely the state the probe exists to reveal.
 */
public class ExecutionPolicyProviderClient implements ProviderClient {

    private final ProviderClient delegate;
    private final ProviderConfigurationRepository configurationRepository;
    private final ProviderExecutionExecutor executor;
    private final BackoffStrategy backoff;

    public ExecutionPolicyProviderClient(ProviderClient delegate,
                                         ProviderConfigurationRepository configurationRepository,
                                         ProviderExecutionExecutor executor,
                                         BackoffStrategy backoff) {
        this.delegate = delegate;
        this.configurationRepository = configurationRepository;
        this.executor = executor;
        this.backoff = backoff;
    }

    @Override
    public ProviderType supportedType() {
        return delegate.supportedType();
    }

    @Override
    public Set<ProviderCapability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    @Override
    public ProviderToken authenticate(Provider provider) {
        return executor.execute(retryable(provider, ProviderMetrics.OPERATION_AUTHENTICATE),
                () -> delegate.authenticate(provider));
    }

    @Override
    public ProviderToken refreshSession(Provider provider, ProviderSession session) {
        return executor.execute(retryable(provider, "refreshSession"),
                () -> delegate.refreshSession(provider, session));
    }

    @Override
    public List<ProviderTrip> search(ProviderSession session, SearchCriteria criteria) {
        return executor.execute(retryable(resolve(session.providerType()), ProviderMetrics.OPERATION_SEARCH),
                () -> delegate.search(session, criteria));
    }

    @Override
    public ProviderSeatMap getSeatMap(ProviderSession session, String providerTripId) {
        return executor.execute(retryable(resolve(session.providerType()), "getSeatMap"),
                () -> delegate.getSeatMap(session, providerTripId));
    }

    @Override
    public SeatReservation blockSeats(ProviderSession session, String providerTripId, List<SeatNumber> seatNumbers) {
        // Single attempt: a timed-out block may already hold the seats.
        return executor.execute(
                ProviderExecutionPolicy.nonIdempotent(resolve(session.providerType()), "blockSeats"),
                () -> delegate.blockSeats(session, providerTripId, seatNumbers));
    }

    @Override
    public void releaseSeats(ProviderSession session, String providerBlockReference) {
        executor.execute(
                ProviderExecutionPolicy.nonIdempotent(resolve(session.providerType()), "releaseSeats"),
                () -> {
                    delegate.releaseSeats(session, providerBlockReference);
                    return null;
                });
    }

    @Override
    public BookingConfirmation confirmBooking(ProviderSession session, String providerBlockReference,
                                              String providerTripId, List<PassengerDetail> passengers) {
        // Single attempt: retrying a timed-out confirmation is how you double-book someone.
        return executor.execute(
                ProviderExecutionPolicy.nonIdempotent(resolve(session.providerType()), "confirmBooking"),
                () -> delegate.confirmBooking(session, providerBlockReference, providerTripId, passengers));
    }

    @Override
    public ProviderTicket downloadTicket(ProviderSession session, BookingReference bookingReference) {
        return executor.execute(retryable(resolve(session.providerType()), "downloadTicket"),
                () -> delegate.downloadTicket(session, bookingReference));
    }

    @Override
    public ProviderHealthCheck checkHealth(Provider provider) {
        return executor.execute(ProviderExecutionPolicy.nonIdempotent(provider, "checkHealth"),
                () -> delegate.checkHealth(provider));
    }

    private ProviderExecutionPolicy retryable(Provider provider, String operation) {
        return ProviderExecutionPolicy.from(provider, operation, backoff);
    }

    /**
     * Resolves the registry row so the call uses that provider's configured timeout and retry
     * count. A session for a provider that has since been removed from the registry is a genuine
     * error, not something to paper over with defaults — silently falling back would hide a
     * misconfiguration behind timeouts nobody chose.
     */
    private Provider resolve(ProviderType providerType) {
        return configurationRepository.findByType(providerType)
                .orElseThrow(() -> new ProviderNotSupportedException(providerType));
    }
}
