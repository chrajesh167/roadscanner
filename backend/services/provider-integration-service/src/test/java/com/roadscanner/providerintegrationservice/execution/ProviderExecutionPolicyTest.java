package com.roadscanner.providerintegrationservice.execution;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderTimeoutException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderUnavailableException;
import com.roadscanner.providerintegrationservice.domain.exception.ProviderValidationException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderError;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Policy construction, retry classification and backoff arithmetic. */
class ProviderExecutionPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

    private static Provider provider(int timeoutMillis, int retryCount) {
        return Provider.reconstitute(ProviderId.generate(), ProviderType.FLIXBUS, ProviderCategory.BUS, "FlixBus",
                true, Set.of(), "https://partner.test", timeoutMillis, retryCount, NOW, NOW);
    }

    @Nested
    class Policy {

        @Test
        void takesTimeoutAndAttemptsFromTheProviderRow() {
            ProviderExecutionPolicy policy =
                    ProviderExecutionPolicy.from(provider(7_500, 3), "search", BackoffStrategy.none());

            assertThat(policy.timeout()).isEqualTo(Duration.ofMillis(7_500));
            // retry_count of 3 means three retries after the first call.
            assertThat(policy.maxAttempts()).isEqualTo(4);
            assertThat(policy.allowsRetry()).isTrue();
        }

        @Test
        void aProviderConfiguredWithZeroRetriesStillGetsOneAttempt() {
            ProviderExecutionPolicy policy =
                    ProviderExecutionPolicy.from(provider(5_000, 0), "search", BackoffStrategy.none());

            assertThat(policy.maxAttempts()).isEqualTo(1);
            assertThat(policy.allowsRetry()).isFalse();
        }

        @Test
        void aNonIdempotentPolicyKeepsTheTimeoutButNeverRepeats() {
            ProviderExecutionPolicy policy = ProviderExecutionPolicy.nonIdempotent(provider(9_000, 5), "confirmBooking");

            // The timeout still protects the caller; only the repetition is removed, because a
            // retried confirmation is how you double-book someone.
            assertThat(policy.timeout()).isEqualTo(Duration.ofMillis(9_000));
            assertThat(policy.maxAttempts()).isEqualTo(1);
            assertThat(policy.allowsRetry()).isFalse();
        }

        @Test
        void rejectsAnInvalidPolicy() {
            assertThatThrownBy(() -> new ProviderExecutionPolicy(ProviderType.FLIXBUS, "search", Duration.ZERO, 1,
                    BackoffStrategy.none(), RetryStrategy.never()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timeout");

            assertThatThrownBy(() -> new ProviderExecutionPolicy(ProviderType.FLIXBUS, "  ", Duration.ofSeconds(1), 1,
                    BackoffStrategy.none(), RetryStrategy.never()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("operation");

            assertThatThrownBy(() -> new ProviderExecutionPolicy(ProviderType.FLIXBUS, "search",
                    Duration.ofSeconds(1), 0, BackoffStrategy.none(), RetryStrategy.never()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxAttempts");
        }
    }

    @Nested
    class Retrying {

        private final RetryStrategy strategy = RetryStrategy.retryableFailuresOnly();

        private static ProviderUnavailableException retryable() {
            return new ProviderUnavailableException("down",
                    new ProviderError(ProviderType.FLIXBUS, "PROVIDER_UNAVAILABLE", "down", true));
        }

        @Test
        void retriesARetryableFailureWhileAttemptsRemain() {
            assertThat(strategy.shouldRetry(1, 3, retryable())).isTrue();
            assertThat(strategy.shouldRetry(2, 3, retryable())).isTrue();
        }

        @Test
        void stopsAtTheAttemptCeiling() {
            assertThat(strategy.shouldRetry(3, 3, retryable())).isFalse();
        }

        @Test
        void neverRetriesANonRetryableFailure() {
            assertThat(strategy.shouldRetry(1, 5,
                    new ProviderValidationException(ProviderType.FLIXBUS, "search", "bad date", null))).isFalse();
        }

        @Test
        void retriesATimeout() {
            assertThat(strategy.shouldRetry(1, 3,
                    new ProviderTimeoutException(ProviderType.FLIXBUS, "search", Duration.ofSeconds(1), null)))
                    .isTrue();
        }

        @Test
        void neverRetriesAFailureFromOutsideTheProviderHierarchy() {
            // A bug in our own code repeats identically; retrying just produces it again.
            assertThat(strategy.shouldRetry(1, 5, new IllegalStateException("our bug"))).isFalse();
        }

        @Test
        void neverRetriesAProviderFailureCarryingNoErrorClassification() {
            ProviderUnavailableException unclassified = new ProviderUnavailableException("no error attached", null);

            // Absent classification is treated as "do not retry" — the safe default when nobody
            // decided whether repeating is sound.
            assertThat(strategy.shouldRetry(1, 5, unclassified)).isFalse();
        }

        @Test
        void theNeverStrategyRefusesEvenARetryableFailure() {
            assertThat(RetryStrategy.never().shouldRetry(1, 5, retryable())).isFalse();
        }
    }

    @Nested
    class Backoff {

        private final BackoffStrategy deterministic =
                new ExponentialBackoffStrategy(Duration.ofMillis(100), 2.0, Duration.ofSeconds(2), false);

        @Test
        void growsExponentially() {
            assertThat(deterministic.delayAfter(1)).isEqualTo(Duration.ofMillis(100));
            assertThat(deterministic.delayAfter(2)).isEqualTo(Duration.ofMillis(200));
            assertThat(deterministic.delayAfter(3)).isEqualTo(Duration.ofMillis(400));
        }

        @Test
        void capsTheDelay() {
            // Without a cap, a provider configured with 5 retries would hold a request thread for
            // far longer than the point of retrying justifies.
            assertThat(deterministic.delayAfter(10)).isEqualTo(Duration.ofSeconds(2));
        }

        @Test
        void jitterStaysWithinTheCappedInterval() {
            BackoffStrategy jittered =
                    new ExponentialBackoffStrategy(Duration.ofMillis(100), 2.0, Duration.ofSeconds(2), true);

            assertThat(IntStream.range(0, 200).mapToObj(i -> jittered.delayAfter(3)))
                    .allSatisfy(delay -> assertThat(delay)
                            .isBetween(Duration.ZERO, Duration.ofMillis(400)));
        }

        @Test
        void jitterActuallyVaries() {
            BackoffStrategy jittered =
                    new ExponentialBackoffStrategy(Duration.ofMillis(500), 2.0, Duration.ofSeconds(5), true);

            // Identical delays would leave a recovering provider facing synchronised retry waves —
            // the exact failure mode jitter exists to prevent.
            assertThat(IntStream.range(0, 50).mapToObj(i -> jittered.delayAfter(2)).distinct().count())
                    .isGreaterThan(1);
        }

        @Test
        void noneNeverWaits() {
            assertThat(BackoffStrategy.none().delayAfter(5)).isEqualTo(Duration.ZERO);
        }

        @Test
        void rejectsAnAttemptBelowOne() {
            assertThatThrownBy(() -> deterministic.delayAfter(0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsInvalidConfiguration() {
            assertThatThrownBy(() -> new ExponentialBackoffStrategy(Duration.ofMillis(-1), 2.0,
                    Duration.ofSeconds(1), false)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ExponentialBackoffStrategy(Duration.ofMillis(100), 0.5,
                    Duration.ofSeconds(1), false)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ExponentialBackoffStrategy(Duration.ofSeconds(5), 2.0,
                    Duration.ofSeconds(1), false)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("max backoff");
        }
    }
}
