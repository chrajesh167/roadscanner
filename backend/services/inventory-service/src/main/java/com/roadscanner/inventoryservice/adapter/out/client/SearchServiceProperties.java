package com.roadscanner.inventoryservice.adapter.out.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/** Connection details for {@code search-service}, which owns the platform's single
 * provider-location mapping table. Same convention as
 * {@link ProviderIntegrationServiceProperties}: no hardcoded fallback, a missing base URL fails
 * startup loudly rather than silently pointing at nothing. */
@ConfigurationProperties(prefix = "roadscanner.search-service")
public record SearchServiceProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public SearchServiceProperties {
        Objects.requireNonNull(baseUrl, "roadscanner.search-service.base-url must be set");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("roadscanner.search-service.base-url must not be blank");
        }
        Objects.requireNonNull(connectTimeout, "roadscanner.search-service.connect-timeout must be set");
        Objects.requireNonNull(readTimeout, "roadscanner.search-service.read-timeout must be set");
    }
}
