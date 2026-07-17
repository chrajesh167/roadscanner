package com.roadscanner.searchservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * The canonical identity of a bus operator, owned by {@code operator-service}. {@code
 * search-service} references it as an opaque foreign value — the same pattern
 * {@code auth-service}'s {@code UserId} uses for its own foreign identifiers.
 */
public record OperatorId(UUID value) {

    public OperatorId {
        Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
