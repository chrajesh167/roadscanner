package com.roadscanner.searchservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * Google Places configuration.
 *
 * <p>{@code apiKey} is sourced from {@code GOOGLE_PLACES_API_KEY} and is <strong>server-side
 * only</strong>. No endpoint echoes it, no DTO carries it, and it is never rendered into a page:
 * the browser calls {@code GET /api/v1/google/places}, and this service — not the browser — calls
 * Google. A key shipped to the frontend would be extractable by anyone who opens devtools and
 * chargeable to this project, which is why the proxy endpoint exists at all rather than the SPA
 * calling Google directly.
 *
 * <p>{@code enabled} exists so the integration can be switched off without removing configuration
 * — the local and test profiles run with it off, and the endpoint then answers 503 rather than
 * attempting a call with a placeholder key.
 *
 * @param cacheTtl     how long a successful autocomplete answer stays cached
 * @param cacheMaxSize maximum cached queries; the oldest are evicted past this
 * @param rateLimitRequestsPerMinute sustained ceiling on calls that reach the provider, per
 *                                   instance — see {@code TokenBucketPlaceAutocompleteRateLimiter}
 * @param rateLimitBurstSize         how many permits may accumulate, so a user typing quickly is
 *                                   not refused for behaving normally
 */
@ConfigurationProperties(prefix = "roadscanner.google-places")
public record GooglePlacesProperties(boolean enabled, String apiKey, String baseUrl, Duration requestTimeout,
                                     Duration cacheTtl, int cacheMaxSize, String language, String region,
                                     int rateLimitRequestsPerMinute, int rateLimitBurstSize) {

    public GooglePlacesProperties {
        Objects.requireNonNull(baseUrl, "roadscanner.google-places.base-url must be set");
        Objects.requireNonNull(requestTimeout, "roadscanner.google-places.request-timeout must be set");
        Objects.requireNonNull(cacheTtl, "roadscanner.google-places.cache-ttl must be set");
        if (cacheMaxSize < 1) {
            throw new IllegalArgumentException("roadscanner.google-places.cache-max-size must be at least 1");
        }
        if (rateLimitRequestsPerMinute < 1) {
            throw new IllegalArgumentException(
                    "roadscanner.google-places.rate-limit-requests-per-minute must be at least 1");
        }
        if (rateLimitBurstSize < 1) {
            throw new IllegalArgumentException(
                    "roadscanner.google-places.rate-limit-burst-size must be at least 1");
        }
    }

    /**
     * Enabled <em>and</em> actually usable. Treating a blank key as "not configured" rather than
     * letting it through means a misconfigured deployment surfaces as an honest 503 instead of a
     * stream of 401s from Google.
     */
    public boolean isUsable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
