package com.roadscanner.providerintegrationservice.adapter.out.provider.flixbus;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the FlixBus adapter — {@code roadscanner.provider.flixbus.*} in
 * {@code application.yml}. RoadScanner has no real FlixBus B2B credentials or base URL today
 * (see README.md "Remaining Integration Points"), so {@code baseUrl}/{@code clientId}/
 * {@code clientSecret} are placeholder-shaped local values in every profile and the
 * {@code FLIXBUS} row in {@code provider_configurations} is seeded {@code enabled=false} — the
 * adapter is fully implemented and tested against {@link FlixBusMapper}'s documented contract
 * via {@code MockRestServiceServer}, just not pointed at a real endpoint yet.
 */
@ConfigurationProperties(prefix = "roadscanner.provider.flixbus")
public record FlixBusProperties(String baseUrl, String clientId, String clientSecret, Duration connectTimeout,
                                 Duration readTimeout) {
}
