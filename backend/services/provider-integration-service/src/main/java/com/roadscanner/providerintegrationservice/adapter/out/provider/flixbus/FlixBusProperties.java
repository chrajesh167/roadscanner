package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Non-secret configuration for the FlixBus adapter — {@code roadscanner.provider.flixbus.*} in
 * {@code application.yml}.
 *
 * <p><strong>Carries no credentials.</strong> {@code clientId}/{@code clientSecret} were removed in
 * Sprint 3B: partner secrets live in {@code provider_credentials}, encrypted, and are resolved
 * through {@link FlixBusCredentials}. Leaving a second, config-shaped source of the same secret
 * would mean an operator rotating it through the admin API kept authenticating with the stale copy
 * until someone redeployed. {@code FlixBusConfigurationCarriesNoSecretsTest} enforces this.
 *
 * <p>RoadScanner has no real FlixBus B2B base URL today (see README.md "Remaining Integration
 * Points"), so {@code baseUrl} is a placeholder in every profile and the {@code FLIXBUS} row in
 * {@code provider_configurations} is seeded {@code enabled=false} — the adapter is implemented and
 * tested against {@link FlixBusMapper}'s documented contract via {@code MockRestServiceServer},
 * just not pointed at a real endpoint yet.
 */
@ConfigurationProperties(prefix = "roadscanner.provider.flixbus")
public record FlixBusProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
