package com.roadscanner.searchservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * Operational tuning knobs for search behavior — externalized rather than hardcoded, the same
 * "domain enforces the shape, configuration enforces the baseline" split
 * {@code auth-service}'s {@code AuthProperties} follows. See
 * docs/services/search-service/domain-model.md ({@link com.roadscanner.searchservice.domain.model.SearchQuery}'s
 * absolute page-size ceiling) and boundaries.md (the availability cache TTL) for the domain-level
 * rules these values feed.
 */
@ConfigurationProperties(prefix = "roadscanner.search")
public record SearchProperties(Pagination pagination, Suggestions suggestions, Availability availability, Kafka kafka) {

    public SearchProperties {
        Objects.requireNonNull(pagination, "roadscanner.search.pagination must be set");
        Objects.requireNonNull(suggestions, "roadscanner.search.suggestions must be set");
        Objects.requireNonNull(availability, "roadscanner.search.availability must be set");
        Objects.requireNonNull(kafka, "roadscanner.search.kafka must be set");
    }

    public record Pagination(int defaultPageSize, int maxPageSize) {
        public Pagination {
            if (defaultPageSize < 1) {
                throw new IllegalArgumentException("roadscanner.search.pagination.default-page-size must be positive");
            }
            if (maxPageSize < defaultPageSize) {
                throw new IllegalArgumentException(
                        "roadscanner.search.pagination.max-page-size must be at least default-page-size");
            }
        }
    }

    public record Suggestions(int maxResults) {
        public Suggestions {
            if (maxResults < 1) {
                throw new IllegalArgumentException("roadscanner.search.suggestions.max-results must be positive");
            }
        }
    }

    public record Availability(Duration cacheTtl, Duration requestTimeout) {
        public Availability {
            Objects.requireNonNull(cacheTtl, "roadscanner.search.availability.cache-ttl must be set");
            Objects.requireNonNull(requestTimeout, "roadscanner.search.availability.request-timeout must be set");
            if (cacheTtl.isNegative() || cacheTtl.isZero()) {
                throw new IllegalArgumentException("roadscanner.search.availability.cache-ttl must be positive");
            }
            if (requestTimeout.isNegative() || requestTimeout.isZero()) {
                throw new IllegalArgumentException("roadscanner.search.availability.request-timeout must be positive");
            }
        }
    }

    public record Kafka(String tripEventsTopic, String reviewEventsTopic) {
        public Kafka {
            Objects.requireNonNull(tripEventsTopic, "roadscanner.search.kafka.trip-events-topic must be set");
            Objects.requireNonNull(reviewEventsTopic, "roadscanner.search.kafka.review-events-topic must be set");
            if (tripEventsTopic.isBlank()) {
                throw new IllegalArgumentException("roadscanner.search.kafka.trip-events-topic must not be blank");
            }
            if (reviewEventsTopic.isBlank()) {
                throw new IllegalArgumentException("roadscanner.search.kafka.review-events-topic must not be blank");
            }
        }
    }
}
