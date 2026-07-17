package com.roadscanner.searchservice.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A trip's bus category and amenities, as denormalized from {@code operator-service}'s fleet
 * data (docs/services/search-service/domain-model.md). {@code category} is a free-form label
 * ("AC Sleeper", "Non-AC Seater", etc.) rather than a fixed enum — {@code operator-service}
 * owns the taxonomy of bus types, not {@code search-service}, and coupling this value object to
 * a fixed set of categories would require a search-service release every time the platform adds
 * one (docs/architecture/service-boundaries.md's boundary principle: a service should not need
 * to change for a reason that belongs to another service).
 */
public record BusType(String category, List<String> amenities) {

    public BusType {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(amenities, "amenities must not be null");
        category = category.trim();
        if (category.isEmpty()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        amenities = List.copyOf(amenities);
    }
}
