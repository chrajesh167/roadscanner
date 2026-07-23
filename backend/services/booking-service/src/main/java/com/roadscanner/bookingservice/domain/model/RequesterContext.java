package com.roadscanner.bookingservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * The identity `api-gateway` authenticated and this service's own JWT verification confirmed —
 * carried explicitly through every inbound port's {@code Command} that needs an ownership or
 * role decision, rather than reaching for a Spring Security type inside the application layer
 * (docs/services/booking-service/boundaries.md's "Booking ↔ Auth": authorization is "decided
 * entirely within `booking-service`, using only its own data").
 */
public record RequesterContext(UUID requesterId, Role role) {

    public RequesterContext {
        Objects.requireNonNull(requesterId, "requesterId must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }

    public boolean isPrivileged() {
        return role == Role.ADMIN || role == Role.SUPPORT;
    }
}
