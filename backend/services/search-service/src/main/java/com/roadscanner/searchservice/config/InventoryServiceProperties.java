package com.roadscanner.searchservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * Connection details for the one synchronous dependency this service has —
 * {@code inventory-service}'s "Trip Availability Query" (docs/services/search-service/boundaries.md).
 * No hardcoded fallback, matching {@code auth-service}'s convention for every environment-specific
 * value: a missing base URL should fail startup loudly, not silently default to something wrong.
 */
@ConfigurationProperties(prefix = "roadscanner.inventory-service")
public record InventoryServiceProperties(String baseUrl) {

    public InventoryServiceProperties {
        Objects.requireNonNull(baseUrl, "roadscanner.inventory-service.base-url must be set");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("roadscanner.inventory-service.base-url must not be blank");
        }
    }
}
