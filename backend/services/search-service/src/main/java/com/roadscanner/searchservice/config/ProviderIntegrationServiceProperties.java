package com.roadscanner.searchservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * Where provider-integration-service lives, and how long to wait for it.
 *
 * <p>Provider timeouts and retries are that service's concern, configured per provider on its own
 * registry row. This timeout is only the hop between the two services, and is deliberately the
 * larger of the two: it must outlast a provider call that is itself already retrying, or this
 * client would give up while a perfectly healthy request was still in flight.
 */
@ConfigurationProperties(prefix = "roadscanner.provider-integration-service")
public record ProviderIntegrationServiceProperties(String baseUrl, Duration requestTimeout) {

    public ProviderIntegrationServiceProperties {
        Objects.requireNonNull(baseUrl, "roadscanner.provider-integration-service.base-url must be set");
        Objects.requireNonNull(requestTimeout,
                "roadscanner.provider-integration-service.request-timeout must be set");
    }
}
