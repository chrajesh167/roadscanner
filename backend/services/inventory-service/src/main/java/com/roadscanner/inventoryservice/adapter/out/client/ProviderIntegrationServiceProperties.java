package com.roadscanner.inventoryservice.adapter.out.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/** Connection details for the one synchronous dependency this service has —
 * {@code provider-integration-service} — matching {@code search-service}'s
 * {@code InventoryServiceProperties} convention exactly: no hardcoded fallback, a missing base
 * URL fails startup loudly. */
@ConfigurationProperties(prefix = "roadscanner.provider-integration-service")
public record ProviderIntegrationServiceProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public ProviderIntegrationServiceProperties {
        Objects.requireNonNull(baseUrl, "roadscanner.provider-integration-service.base-url must be set");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("roadscanner.provider-integration-service.base-url must not be blank");
        }
        Objects.requireNonNull(connectTimeout, "roadscanner.provider-integration-service.connect-timeout must be set");
        Objects.requireNonNull(readTimeout, "roadscanner.provider-integration-service.read-timeout must be set");
    }
}
