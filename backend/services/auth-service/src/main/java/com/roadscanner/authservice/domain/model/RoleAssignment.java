package com.roadscanner.authservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A single role-assignment event for a user — per
 * docs/services/auth-service/database-design.md, modeled as its own concept rather than a
 * mutable column on {@link Credential} specifically so role history/audit is a natural
 * extension: each assignment is a new, immutable fact, never edited in place. A user's
 * <em>current</em> role is "the latest RoleAssignment for that user", a query answered by
 * {@link com.roadscanner.authservice.domain.port.out.CredentialRepository}'s persistence
 * adapter — not something a single RoleAssignment instance can determine on its own.
 *
 * Implemented as a record, not a mutable entity class like {@link Credential}: it has no
 * behavior or state transitions after creation, so there is nothing for encapsulated mutation
 * to protect. This is a deliberate exception to "entities are mutable classes" — an
 * append-only, event-like domain concept is exactly what a record is for.
 */
public record RoleAssignment(UserId userId, Role role, AssignedBy assignedBy, Instant assignedAt) {

    public RoleAssignment {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(assignedBy, "assignedBy must not be null");
        Objects.requireNonNull(assignedAt, "assignedAt must not be null");
    }

    public static RoleAssignment assign(UserId userId, Role role, AssignedBy assignedBy, Instant now) {
        return new RoleAssignment(userId, role, assignedBy, now);
    }
}
