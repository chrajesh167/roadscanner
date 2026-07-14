package com.roadscanner.authservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * The canonical identity of a user. Shared, by value, with user-service and every other
 * service on the platform — see docs/services/auth-service/responsibilities.md ("Boundary
 * With User Service"). auth-service is the only service that owns a Credential keyed by this
 * id; every other service that references it does so as an opaque foreign value.
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
