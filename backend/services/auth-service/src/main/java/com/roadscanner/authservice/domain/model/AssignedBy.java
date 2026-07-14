package com.roadscanner.authservice.domain.model;

import java.util.Objects;

/**
 * Who or what triggered a {@link RoleAssignment} — either an admin's {@link UserId} or a
 * calling service's name (e.g. {@code "operator-service"}, per
 * docs/services/auth-service/responsibilities.md's role-elevation flow, which is a
 * synchronous, admin/service-triggered call, never self-service). Modeled as a single string
 * value rather than two separate types because every current caller only needs it for audit
 * display, not for branching logic — see docs/services/auth-service/database-design.md
 * ("assigned-by").
 */
public record AssignedBy(String value) {

    public AssignedBy {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static AssignedBy admin(UserId adminUserId) {
        Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        return new AssignedBy("admin:" + adminUserId);
    }

    public static AssignedBy service(String serviceName) {
        Objects.requireNonNull(serviceName, "serviceName must not be null");
        return new AssignedBy("service:" + serviceName);
    }

    @Override
    public String toString() {
        return value;
    }
}
