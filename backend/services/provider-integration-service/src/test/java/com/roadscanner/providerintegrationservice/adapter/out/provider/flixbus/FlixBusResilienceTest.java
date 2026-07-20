package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import com.roadscanner.providerintegrationservice.domain.exception.ProviderUnavailableException;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.testsupport.TestcontainersConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves the {@code flixbus} Resilience4j circuit breaker actually opens under sustained failure
 * and that {@code FlixBusExceptionTranslator#translateFallback} is what callers see once it does
 * — not a unit test of Resilience4j itself, but of this service's wiring of it
 * ({@code application.yml}'s {@code resilience4j.*} config plus the {@code @CircuitBreaker}/
 * {@code @Retry} annotations on every {@code FlixBus*Client} method).
 *
 * Points {@code roadscanner.provider.flixbus.base-url} at a connection that always refuses, and
 * shortens every relevant timing property so the test runs in well under a second per call
 * instead of waiting out the production-sized timeouts in {@code application.yml}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FlixBusResilienceTest {

    @DynamicPropertySource
    static void fastFailureProperties(DynamicPropertyRegistry registry) {
        registry.add("roadscanner.provider.flixbus.base-url", () -> "http://127.0.0.1:1");
        registry.add("roadscanner.provider.flixbus.connect-timeout", () -> "PT0.2S");
        registry.add("roadscanner.provider.flixbus.read-timeout", () -> "PT0.2S");
        registry.add("resilience4j.retry.instances.flixbus.wait-duration", () -> "PT0.05S");
        registry.add("resilience4j.circuitbreaker.instances.flixbus.minimum-number-of-calls", () -> "4");
        registry.add("resilience4j.circuitbreaker.instances.flixbus.sliding-window-size", () -> "4");
    }

    @Autowired
    private FlixBusProviderClientAdapter flixBusProviderClientAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void repeatedConnectionFailuresOpenTheCircuitBreakerAndSurfaceAsProviderUnavailable() {
        Provider flixbus = Provider.reconstitute(ProviderId.generate(), ProviderType.FLIXBUS, "FlixBus", true,
                Set.of(), "http://127.0.0.1:1", Instant.now(), Instant.now());

        for (int i = 0; i < 6; i++) {
            assertThatCallFailsWithProviderUnavailable(flixbus);
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("flixbus");
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(circuitBreaker.getState()).isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN));
    }

    private void assertThatCallFailsWithProviderUnavailable(Provider flixbus) {
        try {
            flixBusProviderClientAdapter.authenticate(flixbus);
            org.junit.jupiter.api.Assertions.fail("Expected authenticate() to fail against an unreachable host");
        } catch (RuntimeException e) {
            assertThat(e).isInstanceOf(ProviderUnavailableException.class);
        }
    }
}
