package com.roadscanner.providerintegrationservice.execution;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderIntegrationException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderTimeoutException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderUnavailableException;
import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs one provider call under a {@link ProviderExecutionPolicy}: timeout, retries, backoff,
 * structured logging and metrics, in one place for every provider.
 *
 * <p>Shared rather than per-adapter because every one of those concerns is identical across
 * providers and none of them is where a provider integration should be spending its complexity.
 * Before this, FlixBus carried resilience4j annotations pinned to a static YAML instance named
 * {@code flixbus}; a second provider meant a second instance, a second set of annotations, and no
 * guarantee the two behaved alike. Here the settings come from the provider row instead.
 *
 * <h2>Why a thread pool</h2>
 *
 * <p>The timeout is enforced by running the call on a worker and bounding the wait, because the
 * per-provider {@code timeout_ms} is dynamic while an {@code HttpClient}'s socket timeout is fixed
 * when the client bean is built. A socket timeout also cannot bound a call that connects fine and
 * then streams slowly forever.
 *
 * <p>The cost is that a timed-out call keeps running on its worker until the socket timeout fires
 * — the caller is released, the thread is not. The pool is therefore bounded and its rejection
 * behaviour is surfaced as an unavailable provider rather than swallowed, so exhausting it is
 * visible instead of silently queueing.
 *
 * <h2>Correlation ids</h2>
 *
 * <p>MDC is thread-local, so a naive submit would lose the correlation id the moment work moved to
 * a worker thread and every provider log line would become untraceable. The calling thread's MDC
 * is captured and reinstated inside the worker, then cleared — leaving it behind would leak one
 * request's correlation id into the next call that reuses the thread, which is worse than having
 * none.
 */
public class ProviderExecutionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProviderExecutionExecutor.class);

    private final ExecutorService executor;
    private final ProviderMetrics metrics;

    public ProviderExecutionExecutor(ExecutorService executor, ProviderMetrics metrics) {
        this.executor = executor;
        this.metrics = metrics;
    }

    /**
     * Executes {@code call} under {@code policy}.
     *
     * @throws ProviderTimeoutException     if an attempt exceeded the policy's timeout and no
     *                                      attempts remain
     * @throws ProviderIntegrationException whatever the call itself failed with, once retries are
     *                                      exhausted — always provider-neutral, never a FlixBus or
     *                                      Spring HTTP type
     */
    public <T> T execute(ProviderExecutionPolicy policy, Callable<T> call) {
        long startedNanos = System.nanoTime();
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                T result = runWithTimeout(policy, call);
                recordOutcome(policy, ProviderMetrics.Outcome.SUCCESS, startedNanos);
                if (attempt > 1) {
                    log.info("Provider call succeeded after retry provider={} operation={} attempt={}",
                            policy.providerType(), policy.operation(), attempt);
                }
                return result;
            } catch (RuntimeException failure) {
                lastFailure = failure;

                if (failure instanceof ProviderTimeoutException) {
                    metrics.recordTimeout(policy.providerType(), policy.operation());
                }

                boolean willRetry = policy.retryStrategy().shouldRetry(attempt, policy.maxAttempts(), failure);
                if (!willRetry) {
                    recordOutcome(policy, statusOf(failure), startedNanos);
                    log.warn("Provider call failed provider={} operation={} attempt={} maxAttempts={} reason={}",
                            policy.providerType(), policy.operation(), attempt, policy.maxAttempts(),
                            failure.getClass().getSimpleName(), failure);
                    throw failure;
                }

                Duration delay = policy.backoff().delayAfter(attempt);
                metrics.recordRetry(policy.providerType(), policy.operation());
                log.warn("Provider call failed, retrying provider={} operation={} attempt={} maxAttempts={} "
                                + "backoffMs={} reason={}",
                        policy.providerType(), policy.operation(), attempt, policy.maxAttempts(),
                        delay.toMillis(), failure.getClass().getSimpleName());

                sleep(delay, policy);
            }
        }

        // Unreachable in practice: the loop either returns or throws. Kept total rather than
        // relying on that, so a future change to the loop cannot silently return null.
        recordOutcome(policy, ProviderMetrics.Outcome.FAILURE, startedNanos);
        throw lastFailure != null ? lastFailure
                : new ProviderUnavailableException("Provider call did not complete",
                unavailableError(policy), null);
    }

    private <T> T runWithTimeout(ProviderExecutionPolicy policy, Callable<T> call) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();

        Future<T> future;
        try {
            future = executor.submit(() -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (callerContext != null) {
                    MDC.setContextMap(callerContext);
                }
                try {
                    return call.call();
                } finally {
                    // Restore, never merely clear: this worker is pooled and the next task must
                    // not inherit this request's correlation id.
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            // The pool is saturated. Surfaced rather than queued so exhaustion is visible.
            throw new ProviderUnavailableException(
                    "No capacity to call provider " + policy.providerType(), unavailableError(policy), e);
        }

        try {
            return future.get(policy.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Interrupt so the worker stops as soon as its own I/O notices; it may still linger
            // until the socket timeout fires, which is why the pool is bounded.
            future.cancel(true);
            throw new ProviderTimeoutException(policy.providerType(), policy.operation(), policy.timeout(), e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException(
                    "Interrupted while calling provider " + policy.providerType(), unavailableError(policy), e);
        } catch (ExecutionException e) {
            throw unwrap(policy, e);
        }
    }

    /**
     * Unwraps the worker's failure. A {@link ProviderIntegrationException} passes through
     * untouched — the adapter already translated it, and rewrapping would bury the specific type
     * the retry strategy needs to read.
     */
    private RuntimeException unwrap(ProviderExecutionPolicy policy, ExecutionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof ProviderIntegrationException providerFailure) {
            return providerFailure;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        // Anything else escaped an adapter untranslated. It is contained here rather than allowed
        // to reach a caller, which is what keeps provider-specific types inside this service.
        //
        // Marked non-retryable: an untranslated exception is almost always a bug in our own
        // mapping code, and repeating it just produces it again — three times slower, and three
        // times the load on a provider that may be fine.
        log.warn("Provider call threw an untranslated exception provider={} operation={} type={}",
                policy.providerType(), policy.operation(), cause.getClass().getName(), cause);
        return new ProviderUnavailableException(
                "Provider " + policy.providerType() + " failed during " + policy.operation(),
                new ProviderError(policy.providerType(), "PROVIDER_CALL_FAILED",
                        "The provider call failed unexpectedly", false),
                cause instanceof Exception ex ? ex : new RuntimeException(cause));
    }

    private void sleep(Duration delay, ProviderExecutionPolicy policy) {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException(
                    "Interrupted while backing off before retrying " + policy.providerType(),
                    unavailableError(policy), e);
        }
    }

    private void recordOutcome(ProviderExecutionPolicy policy, String status, long startedNanos) {
        metrics.recordCall(policy.providerType(), policy.operation(), status,
                Duration.ofNanos(System.nanoTime() - startedNanos));
    }

    private static String statusOf(RuntimeException failure) {
        return failure instanceof ProviderTimeoutException
                ? ProviderMetrics.Outcome.TIMEOUT
                : ProviderMetrics.Outcome.FAILURE;
    }

    private static ProviderError unavailableError(ProviderExecutionPolicy policy) {
        return new ProviderError(policy.providerType(), "PROVIDER_UNAVAILABLE",
                "The provider could not be reached", true);
    }
}
