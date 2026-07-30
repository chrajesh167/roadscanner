package com.roadscanner.providerintegrationservice.execution;

import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * Micrometer instrumentation for provider calls, exported via the existing Prometheus endpoint.
 *
 * <p>Centralised rather than sprinkled through adapters so every provider is measured the same
 * way. A metric that exists for FlixBus but not for the next provider is worse than no metric —
 * it makes a dashboard that silently under-reports.
 *
 * <p>Every meter is tagged {@code provider}, {@code operation} and (where meaningful)
 * {@code status}, which is what lets one dashboard answer "is this provider slow, or this
 * operation, or everything?" without a separate panel per provider.
 *
 * <p>Tag values are bounded — provider codes and operation names both come from a small closed
 * set. Tagging by anything caller-supplied would let one bad request explode Prometheus
 * cardinality.
 */
public class ProviderMetrics {

    static final String CALL_DURATION = "provider.call.duration";
    static final String SEARCH_SUCCESS = "provider.search.success";
    static final String SEARCH_FAILURE = "provider.search.failure";
    static final String SEARCH_TIMEOUT = "provider.search.timeout";
    static final String SEARCH_RETRY = "provider.search.retry";
    static final String AUTH_SUCCESS = "provider.authentication.success";
    static final String AUTH_FAILURE = "provider.authentication.failure";

    static final String TAG_PROVIDER = "provider";
    static final String TAG_OPERATION = "operation";
    static final String TAG_STATUS = "status";

    /** Operation names the dedicated search/authentication counters key off. */
    public static final String OPERATION_SEARCH = "search";
    public static final String OPERATION_AUTHENTICATE = "authenticate";

    private final MeterRegistry registry;

    public ProviderMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records the outcome and duration of one whole call — all attempts included, because that is
     * what the caller actually waited.
     */
    public void recordCall(ProviderType provider, String operation, String status, Duration duration) {
        Timer.builder(CALL_DURATION)
                .description("Wall-clock duration of a provider call, including retries")
                .tag(TAG_PROVIDER, provider.code())
                .tag(TAG_OPERATION, operation)
                .tag(TAG_STATUS, status)
                .register(registry)
                .record(duration);

        if (OPERATION_SEARCH.equals(operation)) {
            counter(Outcome.SUCCESS.equals(status) ? SEARCH_SUCCESS : SEARCH_FAILURE, provider, operation);
        } else if (OPERATION_AUTHENTICATE.equals(operation)) {
            counter(Outcome.SUCCESS.equals(status) ? AUTH_SUCCESS : AUTH_FAILURE, provider, operation);
        }
    }

    /** A timeout is also counted as a failure by {@link #recordCall}; this names it specifically. */
    public void recordTimeout(ProviderType provider, String operation) {
        if (OPERATION_SEARCH.equals(operation)) {
            counter(SEARCH_TIMEOUT, provider, operation);
        }
    }

    /**
     * One retry attempt. Counted per retry rather than per call, so the ratio against
     * {@code provider.call.duration} count shows how much hidden work a provider is costing —
     * the signal that a provider is degrading well before its failure rate moves.
     */
    public void recordRetry(ProviderType provider, String operation) {
        if (OPERATION_SEARCH.equals(operation)) {
            counter(SEARCH_RETRY, provider, operation);
        }
        counter("provider.call.retry", provider, operation);
    }

    private void counter(String name, ProviderType provider, String operation) {
        registry.counter(name, TAG_PROVIDER, provider.code(), TAG_OPERATION, operation).increment();
    }

    /** Status tag values. A closed set, so the tag stays low-cardinality. */
    public static final class Outcome {
        public static final String SUCCESS = "success";
        public static final String FAILURE = "failure";
        public static final String TIMEOUT = "timeout";

        private Outcome() {
        }
    }
}
