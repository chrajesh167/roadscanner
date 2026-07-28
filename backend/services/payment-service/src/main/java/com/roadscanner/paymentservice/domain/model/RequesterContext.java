package com.roadscanner.paymentservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * The identity {@code api-gateway} authenticated and this service's own JWT verification confirmed
 * — carried explicitly through every client-facing port's {@code Command} that needs an ownership
 * or role decision, rather than reaching for a Spring Security type inside the application layer
 * (docs/services/payment-service/boundaries.md's "Payment &harr; Auth": authorization is decided
 * within {@code payment-service}, using only its own data).
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
