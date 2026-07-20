package com.roadscanner.providerintegrationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Operational tuning knobs — {@code roadscanner.provider.*} in {@code application.yml}. Values
 * here are the platform baseline, overridable per environment (matching
 * {@code search-service}'s {@code SearchProperties} convention). */
@ConfigurationProperties(prefix = "roadscanner.provider")
public record ProviderProperties(Duration healthCheckInterval, Duration sessionExpirySweepInterval,
                                  Duration capabilitiesCacheTtl, Duration seatMapCacheTtl, Kafka kafka) {

    public record Kafka(String auditTopic) {
    }
}
