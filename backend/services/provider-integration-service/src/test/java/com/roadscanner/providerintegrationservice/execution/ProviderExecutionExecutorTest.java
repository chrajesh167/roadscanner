package com.roadscanner.providerintegrationservice.execution;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderIntegrationException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderTimeoutException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderUnavailableException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderValidationException;
import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The execution layer's behaviour: timeout enforcement, which failures repeat, backoff, metrics,
 * and correlation-id propagation across the worker boundary.
 */
class ProviderExecutionExecutorTest {

    private static final ProviderType FLIXBUS = ProviderType.FLIXBUS;

    private ExecutorService pool;
    private MeterRegistry meterRegistry;
    private ProviderExecutionExecutor executor;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(4);
        meterRegistry = new SimpleMeterRegistry();
        executor = new ProviderExecutionExecutor(pool, new ProviderMetrics(meterRegistry));
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        MDC.clear();
    }

    private static ProviderExecutionPolicy policy(int maxAttempts, Duration timeout) {
        return new ProviderExecutionPolicy(FLIXBUS, "search", timeout, maxAttempts,
                BackoffStrategy.none(), RetryStrategy.retryableFailuresOnly());
    }

    private static ProviderUnavailableException retryableFailure() {
        return new ProviderUnavailableException("transient",
                new ProviderError(FLIXBUS, "PROVIDER_UNAVAILABLE", "down", true));
    }

    private double counter(String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0 : counter.count();
    }

    private long timerCount(String status) {
        var timer = meterRegistry.find(ProviderMetrics.CALL_DURATION).tag("status", status).timer();
        return timer == null ? 0 : timer.count();
    }

    @Test
    void returnsTheResultOfASuccessfulCall() {
        String result = executor.execute(policy(3, Duration.ofSeconds(2)), () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(timerCount(ProviderMetrics.Outcome.SUCCESS)).isEqualTo(1);
        assertThat(counter(ProviderMetrics.SEARCH_SUCCESS)).isEqualTo(1);
    }

    @Test
    void callsOnlyOnceWhenTheCallSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        executor.execute(policy(3, Duration.ofSeconds(2)), () -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertThat(calls).hasValue(1);
        assertThat(counter(ProviderMetrics.SEARCH_RETRY)).isZero();
    }

    @Test
    void retriesARetryableFailureUpToTheConfiguredAttempts() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(policy(3, Duration.ofSeconds(2)), () -> {
            calls.incrementAndGet();
            throw retryableFailure();
        })).isInstanceOf(ProviderUnavailableException.class);

        // maxAttempts counts the first call, so 3 means one call plus two retries.
        assertThat(calls).hasValue(3);
        assertThat(counter(ProviderMetrics.SEARCH_RETRY)).isEqualTo(2);
        assertThat(counter(ProviderMetrics.SEARCH_FAILURE)).isEqualTo(1);
    }

    @Test
    void stopsRetryingAsSoonAsAnAttemptSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute(policy(5, Duration.ofSeconds(2)), () -> {
            if (calls.incrementAndGet() < 3) {
                throw retryableFailure();
            }
            return "recovered";
        });

        assertThat(result).isEqualTo("recovered");
        assertThat(calls).hasValue(3);
        assertThat(counter(ProviderMetrics.SEARCH_SUCCESS)).isEqualTo(1);
    }

    @Test
    void neverRetriesANonRetryableFailure() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(policy(5, Duration.ofSeconds(2)), () -> {
            calls.incrementAndGet();
            throw new ProviderValidationException(FLIXBUS, "search", "bad date", null);
        })).isInstanceOf(ProviderValidationException.class);

        // Retrying a rejected request burns the provider's rate limit to reach the same answer.
        assertThat(calls).hasValue(1);
        assertThat(counter(ProviderMetrics.SEARCH_RETRY)).isZero();
    }

    @Test
    void neverRetriesWhenThePolicyForbidsIt() {
        AtomicInteger calls = new AtomicInteger();
        ProviderExecutionPolicy nonIdempotent = new ProviderExecutionPolicy(FLIXBUS, "confirmBooking",
                Duration.ofSeconds(2), 1, BackoffStrategy.none(), RetryStrategy.never());

        assertThatThrownBy(() -> executor.execute(nonIdempotent, () -> {
            calls.incrementAndGet();
            throw retryableFailure();
        })).isInstanceOf(ProviderUnavailableException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void enforcesTheTimeout() {
        long started = System.currentTimeMillis();

        assertThatThrownBy(() -> executor.execute(policy(1, Duration.ofMillis(120)), () -> {
            Thread.sleep(5_000);
            return "never";
        })).isInstanceOf(ProviderTimeoutException.class)
                .hasMessageContaining("timed out");

        // Released promptly rather than waiting out the sleep.
        assertThat(System.currentTimeMillis() - started).isLessThan(3_000);
        assertThat(counter(ProviderMetrics.SEARCH_TIMEOUT)).isEqualTo(1);
        assertThat(timerCount(ProviderMetrics.Outcome.TIMEOUT)).isEqualTo(1);
    }

    @Test
    void retriesATimeoutWhenAttemptsRemain() {
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute(policy(3, Duration.ofMillis(150)), () -> {
            if (calls.incrementAndGet() == 1) {
                Thread.sleep(2_000);
            }
            return "second attempt";
        });

        assertThat(result).isEqualTo("second attempt");
        assertThat(counter(ProviderMetrics.SEARCH_TIMEOUT)).isEqualTo(1);
    }

    @Test
    void reportsTheConfiguredTimeoutOnTheException() {
        assertThatThrownBy(() -> executor.execute(policy(1, Duration.ofMillis(80)), () -> {
            Thread.sleep(3_000);
            return "never";
        })).isInstanceOfSatisfying(ProviderTimeoutException.class,
                e -> assertThat(e.timeout()).isEqualTo(Duration.ofMillis(80)));
    }

    @Test
    void propagatesTheCorrelationIdIntoTheWorkerThread() {
        MDC.put("correlationId", "corr-123");
        AtomicReference<String> seen = new AtomicReference<>();

        executor.execute(policy(1, Duration.ofSeconds(2)), () -> {
            seen.set(MDC.get("correlationId"));
            return "ok";
        });

        // Without this, every provider log line would be untraceable the moment work left the
        // request thread.
        assertThat(seen.get()).isEqualTo("corr-123");
    }

    @Test
    void doesNotLeakTheCorrelationIdIntoTheNextCallOnAPooledThread() throws Exception {
        ExecutorService single = Executors.newSingleThreadExecutor();
        ProviderExecutionExecutor sequential =
                new ProviderExecutionExecutor(single, new ProviderMetrics(new SimpleMeterRegistry()));
        try {
            MDC.put("correlationId", "first-request");
            sequential.execute(policy(1, Duration.ofSeconds(2)), () -> "ok");
            MDC.clear();

            AtomicReference<String> leaked = new AtomicReference<>("unset");
            sequential.execute(policy(1, Duration.ofSeconds(2)), () -> {
                leaked.set(MDC.get("correlationId"));
                return "ok";
            });

            // Leaking one request's id into the next is worse than having none — it attributes
            // work to the wrong trace.
            assertThat(leaked.get()).isNull();
        } finally {
            single.shutdownNow();
            single.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void translatesAnUntranslatedExceptionRatherThanLettingItEscape() {
        assertThatThrownBy(() -> executor.execute(policy(1, Duration.ofSeconds(2)), () -> {
            throw new IllegalStateException("adapter forgot to translate this");
        })).isInstanceOf(ProviderUnavailableException.class)
                .isInstanceOf(ProviderIntegrationException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void doesNotRetryAnUntranslatedException() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(policy(3, Duration.ofSeconds(2)), () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("a bug in our own mapping code");
        })).isInstanceOf(ProviderUnavailableException.class);

        // Repeating a programming error just produces it again.
        assertThat(calls).hasValue(1);
    }

    @Test
    void surfacesPoolExhaustionAsAnUnavailableProvider() throws Exception {
        // Pool of 1 with a queue of 1: the third concurrent call has nowhere to go.
        ExecutorService tiny = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(1));
        ProviderExecutionExecutor saturated =
                new ProviderExecutionExecutor(tiny, new ProviderMetrics(new SimpleMeterRegistry()));
        CountDownLatch release = new CountDownLatch(1);
        try {
            for (int i = 0; i < 2; i++) {
                tiny.submit(() -> release.await(5, TimeUnit.SECONDS));
            }

            // Fails fast and visibly rather than queueing behind a slow provider indefinitely.
            assertThatThrownBy(() -> saturated.execute(policy(1, Duration.ofSeconds(2)), () -> "ok"))
                    .isInstanceOf(ProviderUnavailableException.class)
                    .hasMessageContaining("No capacity");
        } finally {
            release.countDown();
            tiny.shutdownNow();
        }
    }

    @Test
    void recordsCallDurationTaggedByProviderAndOperation() {
        executor.execute(policy(1, Duration.ofSeconds(2)), () -> "ok");

        var timer = meterRegistry.find(ProviderMetrics.CALL_DURATION)
                .tag("provider", "FLIXBUS")
                .tag("operation", "search")
                .tag("status", ProviderMetrics.Outcome.SUCCESS)
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

    @Test
    void measuresTheWholeCallIncludingRetries() {
        executor.execute(new ProviderExecutionPolicy(FLIXBUS, "search", Duration.ofSeconds(2), 3,
                attempt -> Duration.ofMillis(60), RetryStrategy.retryableFailuresOnly()), new java.util.concurrent.Callable<>() {
            private int calls;

            @Override
            public String call() {
                if (++calls < 3) {
                    throw retryableFailure();
                }
                return "ok";
            }
        });

        var timer = meterRegistry.find(ProviderMetrics.CALL_DURATION)
                .tag("status", ProviderMetrics.Outcome.SUCCESS).timer();

        // One measurement for the whole call — what the caller actually waited, backoff included.
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(100);
    }

    @Test
    void letsAnErrorPropagateRatherThanWrappingIt() {
        // An OutOfMemoryError or similar is not a provider failure and must not be dressed up as
        // one — wrapping it would hide a JVM-level problem behind a "provider unavailable" alert.
        assertThatThrownBy(() -> executor.execute(policy(1, Duration.ofSeconds(2)), () -> {
            throw new StackOverflowError("simulated");
        })).isInstanceOf(StackOverflowError.class);
    }

    @Test
    void surfacesAnInterruptDuringBackoffWithoutSwallowingTheFlag() throws Exception {
        ProviderExecutionPolicy slowBackoff = new ProviderExecutionPolicy(FLIXBUS, "search",
                Duration.ofSeconds(2), 3, attempt -> Duration.ofSeconds(30),
                RetryStrategy.retryableFailuresOnly());
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicReference<Boolean> interruptFlag = new AtomicReference<>();

        Thread caller = new Thread(() -> {
            try {
                executor.execute(slowBackoff, () -> {
                    throw retryableFailure();
                });
            } catch (Throwable t) {
                thrown.set(t);
                // Swallowing the interrupt flag would leave the pool unable to shut down cleanly.
                interruptFlag.set(Thread.currentThread().isInterrupted());
            }
        });

        caller.start();
        Thread.sleep(300);
        caller.interrupt();
        caller.join(5_000);

        assertThat(thrown.get()).isInstanceOf(ProviderUnavailableException.class);
        assertThat(thrown.get()).hasMessageContaining("Interrupted");
        assertThat(interruptFlag.get()).isTrue();
    }

    @Test
    void exposesTheConfiguredExecutionProperties() {
        var properties = new com.roadscanner.providerintegrationservice.config.ProviderExecutionProperties(
                Duration.ofMillis(200), 2.0, Duration.ofSeconds(2), true, 32, Duration.ofSeconds(5));

        assertThat(properties.backoffInitial()).isEqualTo(Duration.ofMillis(200));
        assertThat(properties.backoffMultiplier()).isEqualTo(2.0);
        assertThat(properties.backoffMax()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.backoffJitter()).isTrue();
        assertThat(properties.poolSize()).isEqualTo(32);
        assertThat(properties.shutdownGrace()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void rejectsANonPositivePoolSize() {
        // A pool of zero would deadlock every provider call rather than failing loudly.
        assertThatThrownBy(() -> new com.roadscanner.providerintegrationservice.config.ProviderExecutionProperties(
                Duration.ofMillis(200), 2.0, Duration.ofSeconds(2), true, 0, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pool-size");
    }
}
