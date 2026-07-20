package com.roadscanner.providerintegrationservice.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the transition-detection this service's {@code ProviderUnavailable}/{@code ProviderRecovered}
 * events depend on — see {@code CheckProviderHealthService}'s Javadoc for the two transitions
 * that actually publish an event. */
class ProviderHealthTest {

    private static final Instant T0 = Instant.parse("2026-07-01T00:00:00Z");

    @Test
    void firstCheckEverIsAChangeButNotTrackedAsAFailureStreak() {
        ProviderHealth health = ProviderHealth.unknown(ProviderType.MOCK, T0);

        boolean changed = health.recordCheck(new ProviderHealthCheck(ProviderType.MOCK, HealthState.HEALTHY, T0, null));

        assertThat(changed).isTrue();
        assertThat(health.currentState()).isEqualTo(HealthState.HEALTHY);
        assertThat(health.consecutiveFailures()).isZero();
        assertThat(health.lastSuccessAt()).isEqualTo(T0);
    }

    @Test
    void repeatedSameStateIsNotAChange() {
        ProviderHealth health = ProviderHealth.unknown(ProviderType.MOCK, T0);
        health.recordCheck(new ProviderHealthCheck(ProviderType.MOCK, HealthState.HEALTHY, T0, null));

        boolean changed = health.recordCheck(
                new ProviderHealthCheck(ProviderType.MOCK, HealthState.HEALTHY, T0.plusSeconds(30), null));

        assertThat(changed).isFalse();
    }

    @Test
    void consecutiveFailuresAccumulateAcrossUnhealthyChecks() {
        ProviderHealth health = ProviderHealth.unknown(ProviderType.MOCK, T0);
        health.recordCheck(new ProviderHealthCheck(ProviderType.MOCK, HealthState.UNAVAILABLE, T0, "down"));
        health.recordCheck(new ProviderHealthCheck(ProviderType.MOCK, HealthState.UNAVAILABLE, T0.plusSeconds(30), "still down"));

        assertThat(health.consecutiveFailures()).isEqualTo(2);
        assertThat(health.lastFailureAt()).isEqualTo(T0.plusSeconds(30));
    }

    @Test
    void recoveryResetsConsecutiveFailures() {
        ProviderHealth health = ProviderHealth.unknown(ProviderType.MOCK, T0);
        health.recordCheck(new ProviderHealthCheck(ProviderType.MOCK, HealthState.UNAVAILABLE, T0, "down"));

        boolean changed = health.recordCheck(
                new ProviderHealthCheck(ProviderType.MOCK, HealthState.HEALTHY, T0.plusSeconds(30), null));

        assertThat(changed).isTrue();
        assertThat(health.consecutiveFailures()).isZero();
    }
}
