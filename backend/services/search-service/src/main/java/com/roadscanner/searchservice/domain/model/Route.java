package com.roadscanner.searchservice.domain.model;

import java.util.Objects;

/**
 * An origin/destination pair — "what a traveler is searching for," independent of any specific
 * trip (docs/services/search-service/domain-model.md). Validates its own shape regardless of
 * caller, the same discipline {@code auth-service}'s value objects apply (see
 * {@code LoginIdentifier}) — a value object must never be constructible into an invalid state.
 */
public record Route(String origin, String destination) {

    public Route {
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        origin = origin.trim();
        destination = destination.trim();
        if (origin.isEmpty()) {
            throw new IllegalArgumentException("origin must not be blank");
        }
        if (destination.isEmpty()) {
            throw new IllegalArgumentException("destination must not be blank");
        }
        if (origin.equalsIgnoreCase(destination)) {
            throw new IllegalArgumentException("origin and destination must not be the same place");
        }
    }

    @Override
    public String toString() {
        return origin + " -> " + destination;
    }
}
