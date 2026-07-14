package com.roadscanner.authservice.domain.port.in;

import com.roadscanner.authservice.domain.model.AssignedBy;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.model.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Elevates or changes a user's platform role. Per
 * docs/services/auth-service/responsibilities.md, this is never client-facing — callable only
 * by {@code operator-service} or {@code admin-console} via a synchronous, authenticated
 * internal call, not by the user whose role is being changed.
 */
public interface AssignRole {

    AssignRoleResult assign(AssignRoleCommand command);

    record AssignRoleCommand(UserId userId, Role role, AssignedBy assignedBy, Instant now) {
        public AssignRoleCommand {
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(role, "role must not be null");
            Objects.requireNonNull(assignedBy, "assignedBy must not be null");
            Objects.requireNonNull(now, "now must not be null");
        }
    }

    record AssignRoleResult(RoleAssignment assignment) {
        public AssignRoleResult {
            Objects.requireNonNull(assignment, "assignment must not be null");
        }
    }
}
